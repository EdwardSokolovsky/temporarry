#!/bin/bash

#using:
#./archive.sh myfile.txt "MySecretPassword" zip
#./archive.sh myfile.txt.tar.gpg "MySecretPassword" unzip


if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <file> <password> <action: zip|unzip>"
    exit 1
fi

FILE=$1
PASSWORD=$2
ACTION=$3

if [ "$ACTION" == "zip" ]; then
    if [ ! -f "$FILE" ]; then
        echo "Error: File '$FILE' does not exist."
        exit 1
    fi

    TAR_FILE="${FILE}.tar"
    tar -cvf "$TAR_FILE" "$FILE"

    GPG_FILE="${TAR_FILE}.gpg"
    gpg --symmetric --batch --passphrase "$PASSWORD" -o "$GPG_FILE" "$TAR_FILE"

    rm "$TAR_FILE"

    echo "File '$FILE' successfully archived and encrypted as '$GPG_FILE'."

elif [ "$ACTION" == "unzip" ]; then
    if [ ! -f "$FILE" ]; then
        echo "Error: Encrypted file '$FILE' does not exist."
        exit 1
    fi

    TAR_FILE="${FILE%.gpg}"
    gpg --decrypt --batch --passphrase "$PASSWORD" -o "$TAR_FILE" "$FILE"

    tar -xvf "$TAR_FILE"

    rm "$TAR_FILE"

    echo "File '$FILE' successfully decrypted and extracted."

else
    echo "Error: Unknown action '$ACTION'. Use 'zip' or 'unzip'."
    exit 1
fi
