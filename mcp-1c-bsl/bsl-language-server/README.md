# BSL Language Server (JAR)

Сюда нужно положить JAR-файл [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases), чтобы MCP-сервер mcp-1c-bsl использовал его без настройки переменной окружения.

## Как установить

1. Скачайте последний релиз с [Releases](https://github.com/1c-syntax/bsl-language-server/releases) (файл `bsl-language-server-<version>.jar` или аналогичный).
2. Переименуйте или скопируйте JAR в этот каталог с именем **`bsl-language-server.jar`**:

   ```bash
   cd mcp-1c-bsl/bsl-language-server
   # после скачивания, например:
   mv ~/Downloads/bsl-language-server-*.jar bsl-language-server.jar
   ```

3. Соберите и запускайте MCP-сервер из корня проекта или из `java/` — JAR будет найден автоматически.

Приоритет поиска JAR: переменная окружения `BSL_LANGUAGE_SERVER_JAR` → `bsl-language-server/bsl-language-server.jar` (относительно проекта) → `bsl-language-server.jar` в текущей директории.
