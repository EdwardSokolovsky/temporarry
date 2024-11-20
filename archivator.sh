#!/bin/bash

#using:
#./archive.sh myfile.txt "MySecretPassword" zip
#./archive.sh myfile.txt.tar.gpg "MySecretPassword" unzip

# Проверка входных параметров
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <file_or_folder> <password> <action: zip|unzip>"
    exit 1
fi

# Входные параметры
FILE=$1
PASSWORD=$2
ACTION=$3
OUTPUT_DIR="./"  # Папка для извлечения, по умолчанию текущая

# Проверка действия
if [ "$ACTION" == "zip" ]; then
    # Проверка существования файла или папки
    if [ ! -e "$FILE" ]; then
        echo "Error: File or folder '$FILE' does not exist."
        exit 1
    fi

    # Создание архива tar
    TAR_FILE="${FILE}.tar"
    tar -cvf "$TAR_FILE" "$FILE"

    # Шифрование архива с помощью gpg
    GPG_FILE="${TAR_FILE}.gpg"
    gpg --symmetric --batch --passphrase "$PASSWORD" -o "$GPG_FILE" "$TAR_FILE"

    # Удаление исходного tar файла
    rm "$TAR_FILE"

    echo "File or folder '$FILE' successfully archived and encrypted as '$GPG_FILE'."

elif [ "$ACTION" == "unzip" ]; then
    # Проверка наличия зашифрованного файла
    if [ ! -f "$FILE" ]; then
        echo "Error: Encrypted file '$FILE' does not exist."
        exit 1
    fi

    # Расшифровка файла с помощью gpg
    TAR_FILE="${FILE%.gpg}"
    gpg --decrypt --batch --passphrase "$PASSWORD" -o "$TAR_FILE" "$FILE"

    # Разархивирование tar файла в указанную директорию
    tar -xvzf "$TAR_FILE" -C "$OUTPUT_DIR"

    # Удаление расшифрованного tar файла
    rm "$TAR_FILE"

    # Извлечение исходного имени файла (без расширения .tar)
    ORIGINAL_FILE="${TAR_FILE%.tar}"

    # Выводим информацию о восстановленном файле
    echo "File '$ORIGINAL_FILE' successfully decrypted and extracted to '$OUTPUT_DIR'."

else
    echo "Error: Unknown action '$ACTION'. Use 'zip' or 'unzip'."
    exit 1
fi

