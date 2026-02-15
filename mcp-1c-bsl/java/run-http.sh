#!/bin/sh
# Запуск MCP 1C BSL в HTTP-режиме. Требуется Java 17+.
set -e
cd "$(dirname "$0")"

JAR="target/mcp-1c-bsl-0.1.0-all.jar"
if [ ! -f "$JAR" ]; then
  echo "JAR не найден: $JAR"
  echo "Сначала соберите проект (нужна Java 17+):"
  echo "  export JAVA_HOME=/путь/к/jdk-17"
  echo "  ./mvnw clean package -DskipTests"
  exit 1
fi

# Проверка версии Java (нужен 17+)
java_version=$(java -version 2>&1 | head -1)
case "$java_version" in
  *'"17'*|*'"18'*|*'"19'*|*'"20'*|*'"21'*|*'"22'*|*'"23'*) ;;
  *)
    echo "Ошибка: нужна Java 17 или новее. Сейчас: $java_version"
    echo "Установите JDK 17 и задайте JAVA_HOME, например:"
    echo "  sudo apt install openjdk-17-jdk"
    echo "  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
    exit 1
    ;;
esac

exec java -jar "$JAR" --http "$@"
