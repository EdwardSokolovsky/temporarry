import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MouseClickerApp {

    // Переменные с заданными значениями
    private static final Point coordinates = getScreenCenter();
    private static final int periodRangeMin = 30; // Минимальное время (в секундах)
    private static final int periodRangeMax = 360; // Максимальное время (в секундах)
    private static final int countOfTimes = 999; // Количество кликов

    public static void main(String[] args) {
        try {
            Robot robot = new Robot(); // Для управления мышью
            Random random = new Random();

            System.out.printf("Начало выполнения: координаты (%d, %d), количество кликов: %d, интервал: [%d, %d] секунд%n",
                    coordinates.x, coordinates.y, countOfTimes, periodRangeMin, periodRangeMax);

            for (int i = 0; i < countOfTimes; i++) {
                // Генерация случайного интервала времени
                int randomPeriod = periodRangeMin + random.nextInt(periodRangeMax - periodRangeMin + 1);
                System.out.printf("Клик #%d через %d секунд...%n", i + 1, randomPeriod);

                // Ожидание
                TimeUnit.SECONDS.sleep(randomPeriod);

                // Выполнение клика
                performClick(robot, coordinates);
            }

            System.out.println("Выполнение завершено!");
        } catch (AWTException | InterruptedException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    // Получить центр экрана
    private static Point getScreenCenter() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return new Point(screenSize.width / 2, screenSize.height / 2);
    }

    // Выполнить клик мышью
    private static void performClick(Robot robot, Point coordinates) {
        robot.mouseMove(coordinates.x, coordinates.y); // Перемещение мыши
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); // Нажатие левой кнопки мыши
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); // Отпускание кнопки
        System.out.printf("Клик выполнен на координатах (%d, %d)%n", coordinates.x, coordinates.y);
    }
}
