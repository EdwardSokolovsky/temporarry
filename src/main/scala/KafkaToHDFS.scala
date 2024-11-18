import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state._
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration => HadoopConfiguration
import java.util.Properties

object KafkaToHDFS {

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    env.enableCheckpointing(60000) // каждые 60 секунд
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(30000) // минимальная пауза между чекпоинтами
    env.getCheckpointConfig.setCheckpointTimeout(300000) // таймаут чекпоинта

    env.getCheckpointConfig.setCheckpointStorage("file:///tmp/flink-checkpoints")
    env.setStateBackend(new org.apache.flink.contrib.streaming.state.RocksDBStateBackend("file:///tmp/rocksdb"))

    val kafkaStream = env.fromSource(
      createKafkaSource(),
      WatermarkStrategy.noWatermarks(),
      "Kafka Source"
    )

    kafkaStream.addSink(new HdfsRetrySink("hdfs://namenode:8020/output-path", maxRetries = 5, retryIntervalSeconds = 10, healthCheckIntervalSeconds = 1800))

    env.execute("Kafka to HDFS with Retry")
  }

  class HdfsRetrySink(
    outputPath: String,
    maxRetries: Int,
    retryIntervalSeconds: Int,
    healthCheckIntervalSeconds: Int) extends RichSinkFunction[String] with CheckpointedFunction {

    @transient private var lastFailureState: ValueState[Long] = _
    @transient private var hdfsUnavailable: ValueState[Boolean] = _
    @transient private var hadoopConfig: HadoopConfiguration = _
    @transient private var fileSystem: FileSystem = _
    @transient private var timerService: TimerService = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      hadoopConfig = new HadoopConfiguration()
      fileSystem = FileSystem.get(hadoopConfig)
      timerService = getRuntimeContext.getTimerService
    }

    override def initializeState(context: FunctionInitializationContext): Unit = {
      val failureDescriptor = new ValueStateDescriptor[Long]("lastFailureState", TypeInformation.of(classOf[Long]))
      lastFailureState = context.getKeyedStateStore.getState(failureDescriptor)

      val hdfsStateDescriptor = new ValueStateDescriptor[Boolean]("hdfsUnavailable", TypeInformation.of(classOf[Boolean]))
      hdfsUnavailable = context.getKeyedStateStore.getState(hdfsStateDescriptor)
    }

    override def snapshotState(context: FunctionSnapshotContext): Unit = {
      val hdfsUnavailableValue = if (hdfsUnavailable.value() != null) hdfsUnavailable.value() else false
      val lastFailureTime = if (lastFailureState.value() != null) lastFailureState.value() else 0L

      getRuntimeContext.getState(
        new ValueStateDescriptor[Boolean]("hdfsUnavailable", TypeInformation.of(classOf[Boolean]))
      ).update(hdfsUnavailableValue)

      getRuntimeContext.getState(
        new ValueStateDescriptor[Long]("lastFailureState", TypeInformation.of(classOf[Long]))
      ).update(lastFailureTime)
    }

    override def invoke(value: String, context: SinkFunction.Context): Unit = {
      if (hdfsUnavailable.value() != null && hdfsUnavailable.value()) {
        scheduleHdfsRecoveryCheck()
        return
      }

      var retries = 0
      var success = false

      while (retries <= maxRetries && !success) {
        try {
          writeToHdfs(value)
          success = true

          lastFailureState.clear()
        } catch {
          case _: Exception =>
            retries += 1
            if (retries > maxRetries) {
              hdfsUnavailable.update(true)
              lastFailureState.update(System.currentTimeMillis())
              scheduleHdfsRecoveryCheck()
              return
            }
            scheduleRetry(value, context.timestamp())
        }
      }
    }

    private def writeToHdfs(data: String): Unit = {
      val path = new Path(s"$outputPath/part-${System.currentTimeMillis}.txt")
      val stream = fileSystem.create(path, true)
      try {
        stream.writeBytes(data)
      } finally {
        stream.close()
      }
    }

    private def scheduleRetry(value: String, timestamp: Long): Unit = {
      timerService.registerProcessingTimeTimer(timestamp + retryIntervalSeconds * 1000)
    }

    private def scheduleHdfsRecoveryCheck(): Unit = {
      timerService.registerProcessingTimeTimer(System.currentTimeMillis() + healthCheckIntervalSeconds * 1000)
    }

    override def onProcessingTime(timer: Long): Unit = {
      if (isHdfsAvailable()) {
        hdfsUnavailable.update(false)
        lastFailureState.clear()
      } else {
        scheduleHdfsRecoveryCheck()
      }
    }

    private def isHdfsAvailable(): Boolean = {
      try {
        fileSystem.exists(new Path("/"))
      } catch {
        case _: Exception => false
      }
    }
  }

  def createKafkaSource(): KafkaSource[String] = {
    val kafkaProperties = new Properties()
    kafkaProperties.setProperty("bootstrap.servers", "kafka-broker:9092")
    kafkaProperties.setProperty("group.id", "flink-group")

    KafkaSource.builder[String]()
      .setBootstrapServers("kafka-broker:9092")
      .setTopics("input-topic")
      .setGroupId("flink-group")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new KafkaDeserializationSchema[String] {
        override def isEndOfStream(nextElement: String): Boolean = false
        override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): String = {
          new String(record.value(), "UTF-8")
        }
        override def getProducedType: TypeInformation[String] = TypeInformation.of(classOf[String])
      })
      .build()
  }
}
