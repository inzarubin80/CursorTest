# BSL Language Server (JAR)

Сюда кладут JAR [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases), чтобы MCP-сервер mcp-1c-bsl использовал его для анализа и форматирования BSL/1С.

## Текущая версия в каталоге

**v0.28.5** — файл `bsl-language-server-0.28.5-exec.jar` (скачан с [Releases](https://github.com/1c-syntax/bsl-language-server/releases/tag/v0.28.5)).

При настройке MCP укажите полный путь к этому JAR в аргументе `--bsl-language-server-jar` или в переменной `BSL_LANGUAGE_SERVER_JAR`.

## Как обновить

1. Скачайте последний релиз с [Releases](https://github.com/1c-syntax/bsl-language-server/releases) (файл **`bsl-language-server-<version>-exec.jar`**).
2. Положите JAR в этот каталог (имя можно не менять).
3. В конфиге MCP укажите путь к новому файлу, например:  
   `--bsl-language-server-jar .../bsl-language-server/bsl-language-server-0.28.6-exec.jar`

Для авто-поиска без аргумента можно положить JAR с именем **`bsl-language-server.jar`** — тогда сервер найдёт его при запуске из корня проекта или из `java/`.

Приоритет поиска JAR: аргумент `--bsl-language-server-jar` → переменная `BSL_LANGUAGE_SERVER_JAR` → `bsl-language-server/bsl-language-server.jar` (относительно проекта) → `bsl-language-server.jar` в текущей директории.
