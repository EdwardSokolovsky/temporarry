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

# Проверка действия
if [ "$ACTION" == "zip" ]; then
    # Проверка существования файла или папки
    if [ ! -e "$FILE" ]; then
        echo "Error: File or folder '$FILE' does not exist."
        exit 1
    fi

    # Исключить скрипт, если архивация происходит в текущей папке
    if [ "$FILE" == "$HOME" ]; then
        EXCLUDE="--exclude=$(basename $0)"
    else
        EXCLUDE=""
    fi

    # Создание архива tar
    TAR_FILE="${FILE}.tar"
    tar -cvf "$TAR_FILE" $EXCLUDE "$FILE"

    # Шифрование архива с помощью gpg
    GPG_FILE="${TAR_FILE}.gpg"
    gpg --symmetric --batch --passphrase "$PASSWORD" -o "$GPG_FILE" "$TAR_FILE"

    # Удаление исходного tar файла
    rm "$TAR_FILE"

    echo "Folder '$FILE' successfully archived and encrypted as '$GPG_FILE'."

elif [ "$ACTION" == "unzip" ]; then
    # Проверка наличия зашифрованного файла
    if [ ! -f "$FILE" ]; then
        echo "Error: Encrypted file '$FILE' does not exist."
        exit 1
    fi

    # Расшифровка файла с помощью gpg
    TAR_FILE="${FILE%.gpg}"
    gpg --decrypt --batch --passphrase "$PASSWORD" -o "$TAR_FILE" "$FILE"

    # Разархивирование tar файла
    tar -xvf "$TAR_FILE"

    # Удаление расшифрованного tar файла
    rm "$TAR_FILE"

    echo "File '$FILE' successfully decrypted and extracted."

else
    echo "Error: Unknown action '$ACTION'. Use 'zip' or 'unzip'."
    exit 1
fi
