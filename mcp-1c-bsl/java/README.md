# MCP 1C BSL Server (Java)

Версия MCP-сервера **только на Java** для внедрения в организации, где нежелательно использовать Go или несколько сред выполнения. Достаточно **одной JVM**: этот сервер и BSL Language Server — оба JAR, запускаются через `java -jar`.

## Требования

- **Java 17+** (обязательно; Java 11 не подходит — будет ошибка `invalid target release: 17`)
- Сборка: **Maven 3.8+** или встроенный **Maven Wrapper** (`./mvnw` в каталоге `java/`)
- JAR [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases)

Если в системе только Java 11, установите JDK 17 и используйте его для сборки и запуска:
```bash
sudo apt install openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./mvnw clean package -DskipTests
```

## Сборка

С Maven в системе:
```bash
mvn clean package
```

Без Maven (используется wrapper, при первом запуске скачивается Maven):
```bash
./mvnw clean package -DskipTests
```

Исполняемый JAR со всеми зависимостями (fat JAR):

```text
target/mcp-1c-bsl-0.1.0-all.jar
```

Запуск:

- **stdio** (по умолчанию) — для локального подключения из Cursor как subprocess:
  ```bash
  java -jar target/mcp-1c-bsl-0.1.0-all.jar
  ```

- **HTTP** — сервер доступен по URL, разработчику достаточно указать адрес в настройках MCP:
  ```bash
  java -jar target/mcp-1c-bsl-0.1.0-all.jar --http
  ```
  Или используйте скрипт (проверяет наличие JAR и Java 17+): `./run-http.sh`  
  **Важно:** полное имя JAR — `target/mcp-1c-bsl-0.1.0-all.jar` (не `target/mcp-1c-bsl`).  
  По умолчанию порт **8080**. Другой порт: `--http --port 9090` или переменная окружения **`MCP_HTTP_PORT`** (например `MCP_HTTP_PORT=9090`).

  **URL сервиса:** `http://<хост>:<порт>/mcp`  
  Пример: `http://localhost:8080/mcp` или `http://mcp.company.local:8080/mcp`.

## Настройка

Переменная окружения **`BSL_LANGUAGE_SERVER_JAR`** — путь к JAR BSL Language Server.  
Если не задана, используется `bsl-language-server.jar` в текущей директории.

Пример (Windows):

```bat
set BSL_LANGUAGE_SERVER_JAR=C:\tools\bsl\bsl-language-server.jar
```

Пример (Linux/macOS):

```bash
export BSL_LANGUAGE_SERVER_JAR=/opt/bsl/bsl-language-server.jar
```

## Подключение в Cursor

**Вариант 1 — по URL (HTTP):** если MCP-сервер запущен в организации по HTTP, укажите только адрес (Streamable HTTP):

```json
{
  "mcpServers": {
    "1c-bsl": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Или для общего сервера: `"url": "http://mcp.company.local:8080/mcp"`.

**Вариант 2 — локальный процесс (stdio):** запуск JAR на машине разработчика:

```json
{
  "mcpServers": {
    "1c-bsl": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/mcp-1c-bsl-0.1.0-all.jar"],
      "env": {
        "BSL_LANGUAGE_SERVER_JAR": "/path/to/bsl-language-server.jar"
      }
    }
  }
}
```

## Инструменты

| Инструмент     | Описание |
|----------------|----------|
| **bsl_analyze** | Анализ каталога или файла: диагностики (синтаксис, правила) и метрики. Тот же движок, что в SonarQube BSL Plugin. |
| **bsl_format**  | Форматирование файла или каталога по правилам BSL LS. |

Архитектура и связка с SonarQube описаны в [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).
