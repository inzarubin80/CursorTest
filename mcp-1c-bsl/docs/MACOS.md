# Запуск mcp-1c-bsl на macOS

Пошаговая инструкция: установка Java 17, сборка MCP-сервера, настройка BSL Language Server и подключение в Cursor на Mac.

---

## 1. Установка Java 17

MCP-сервер и BSL Language Server требуют **Java 17 или новее**. Проверка версии:

```bash
java -version
```

Должно быть что-то вроде `openjdk version "17.x.x"` или `java version "17.x.x"`. Если версия 11 или ниже — установите JDK 17.

### Вариант A: Homebrew (рекомендуется)

```bash
brew install openjdk@17
```

После установки добавьте в `~/.zshrc` (или `~/.bash_profile` для bash):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/opt/homebrew/opt/openjdk@17")
export PATH="$JAVA_HOME/bin:$PATH"
```

- На **Apple Silicon (M1/M2/M3)** Homebrew обычно ставит в `/opt/homebrew`, путь будет `/opt/homebrew/opt/openjdk@17`.
- На **Intel Mac** — `/usr/local/opt/openjdk@17`.

Примените настройки и проверьте:

```bash
source ~/.zshrc
java -version
```

### Вариант B: SDKMAN

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17.0.9-tem
sdk default java 17.0.9-tem
java -version
```

### Вариант C: Сайт Adoptium (Eclipse Temurin)

Скачайте [macOS .pkg](https://adoptium.net/temurin/releases/?version=17&os=macos) (ARM64 для M1/M2/M3, x64 для Intel), установите через установщик. Затем:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

---

## 2. Клонирование репозитория и каталог проекта

Если проект ещё не склонирован:

```bash
cd ~/Projects   # или ваш каталог
git clone https://github.com/your-org/CursorTest.git
cd CursorTest/mcp-1c-bsl
```

Далее все команды — из корня **mcp-1c-bsl** или из **mcp-1c-bsl/java**. Путь к проекту на Mac обычно вида: `/Users/ваше_имя/.../mcp-1c-bsl`.

---

## 3. Установка BSL Language Server (JAR)

MCP-сервер вызывает BSL Language Server как отдельный JAR. Удобнее всего положить его в каталог проекта.

1. Откройте [Releases BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases) и скачайте последний релиз (файл `bsl-language-server-<version>.jar`, около 110 MB).

2. Создайте каталог и положите туда JAR с фиксированным именем:

```bash
cd /Users/ваше_имя/.../mcp-1c-bsl/bsl-language-server
mv ~/Downloads/bsl-language-server-*.jar bsl-language-server.jar
```

Либо в одну команду из корня mcp-1c-bsl:

```bash
cp ~/Downloads/bsl-language-server-*.jar bsl-language-server/bsl-language-server.jar
```

После этого при запуске MCP из корня проекта или из `java/` JAR будет найден автоматически, переменная окружения не обязательна.

**Альтернатива:** положить JAR в любое место (например `/opt/bsl/bsl-language-server.jar`) и задать переменную окружения:

```bash
export BSL_LANGUAGE_SERVER_JAR=/opt/bsl/bsl-language-server.jar
```

---

## 4. Сборка MCP-сервера

Из корня **mcp-1c-bsl**:

```bash
cd /Users/ваше_имя/.../mcp-1c-bsl/java
./mvnw clean package -DskipTests
```

Если установлен Maven глобально:

```bash
mvn clean package
```

В каталоге `java/target/` должен появиться файл:

```text
java/target/mcp-1c-bsl-0.1.0-all.jar
```

Проверка запуска в режиме stdio (после Ctrl+C можно остановить):

```bash
cd /Users/ваше_имя/.../mcp-1c-bsl
java -jar java/target/mcp-1c-bsl-0.1.0-all.jar
```

В логах может быть предупреждение SLF4J — его можно игнорировать. Сервер ждёт ввода по stdin (для Cursor этого достаточно).

Проверка в режиме HTTP:

```bash
java -jar java/target/mcp-1c-bsl-0.1.0-all.jar --http --port 8080
```

В консоли должно появиться: `MCP 1C BSL: HTTP на http://0.0.0.0:8080/mcp`. Откройте в браузере `http://localhost:8080/mcp` — ответ 400 на GET нормален (MCP по HTTP ожидает POST/SSE).

---

## 5. Подключение в Cursor на Mac

Файл настроек MCP в Cursor на macOS обычно находится здесь:

- **Глобальные настройки:** `~/Library/Application Support/Cursor/User/globalStorage/cursor.mcp/mcp.json`
- **Настройки проекта:** в корне проекта файл `.cursor/mcp.json` (или настройки через Cursor Settings → MCP).

Подставьте **реальный путь** к вашему проекту вместо `YOUR_USER` и пути к JAR.

### Вариант A: Локальный процесс (stdio) — JAR в проекте

Если BSL LS лежит в `mcp-1c-bsl/bsl-language-server/bsl-language-server.jar`, при запуске из корня этого проекта JAR найдётся сам. В конфиге укажите только путь к MCP JAR и при необходимости рабочую директорию:

```json
{
  "mcpServers": {
    "1c-bsl": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USER/Projects/CursorTest/mcp-1c-bsl/java/target/mcp-1c-bsl-0.1.0-all.jar"],
      "cwd": "/Users/YOUR_USER/Projects/CursorTest/mcp-1c-bsl"
    }
  }
}
```

`cwd` должен быть корень **mcp-1c-bsl**, чтобы сработал поиск `bsl-language-server/bsl-language-server.jar`.

### Вариант B: Локальный процесс с явным путём к BSL LS

Если JAR BSL LS лежит не в проекте (например в `/opt/bsl/`):

```json
{
  "mcpServers": {
    "1c-bsl": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USER/Projects/CursorTest/mcp-1c-bsl/java/target/mcp-1c-bsl-0.1.0-all.jar"],
      "env": {
        "BSL_LANGUAGE_SERVER_JAR": "/opt/bsl/bsl-language-server.jar"
      }
    }
  }
}
```

### Вариант C: Подключение по HTTP

Если MCP-сервер запущен на этой же машине по HTTP (например `java -jar ... --http --port 8080`):

```json
{
  "mcpServers": {
    "1c-bsl": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

После сохранения конфига перезапустите Cursor или обновите список MCP-серверов. В списке инструментов должны появиться **bsl_analyze** и **bsl_format**.

---

## 6. Проверка работы

1. Откройте проект с файлами `.bsl` или `.os`.
2. В чате Cursor или через панель MCP вызовите инструмент **bsl_analyze**, указав путь к каталогу или файлу (например путь к папке с BSL в вашем проекте).
3. Если BSL Language Server не найден, в ответе будет сообщение об ошибке с подсказкой положить JAR в `bsl-language-server/` или задать `BSL_LANGUAGE_SERVER_JAR`.

---

## Частые проблемы на Mac

| Проблема | Решение |
|----------|--------|
| `java: command not found` или старая версия | Установите JDK 17 (см. шаг 1), настройте `JAVA_HOME` и `PATH`. |
| `invalid target release: 17` | Сборка идёт под другой Java; выполните `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` и снова `./mvnw clean package`. |
| JAR BSL LS не найден при запуске из Cursor | Убедитесь, что в конфиге MCP задан `cwd` на корень **mcp-1c-bsl** (Вариант A) или задан `BSL_LANGUAGE_SERVER_JAR` (Вариант B). |
| `Permission denied` при `./mvnw` | Выполните `chmod +x mcp-1c-bsl/java/mvnw`. |
| Cursor не видит сервер | Проверьте путь к `mcp-1c-bsl-0.1.0-all.jar` (полный путь без `~`), перезапустите Cursor. |

---

Кратко: установите Java 17 → положите `bsl-language-server.jar` в `mcp-1c-bsl/bsl-language-server/` → соберите `./mvnw clean package` в `java/` → в Cursor укажите путь к `mcp-1c-bsl-0.1.0-all.jar` и при stdio задайте `cwd` на корень **mcp-1c-bsl**.
