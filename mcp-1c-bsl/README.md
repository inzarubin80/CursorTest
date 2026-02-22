# MCP-сервер для 1С (BSL)

MCP-сервер для анализа и форматирования кода 1С (BSL/OneScript) через [BSL Language Server](https://github.com/1c-syntax/bsl-language-server). Тот же движок используется в [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community).

**Реализация только на Java** — в организации достаточно одной JVM.

## Требования

- **Java 17+**
- JAR [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases)

**Где положить JAR (по приоритету):**

1. Переменная окружения **`BSL_LANGUAGE_SERVER_JAR`** — путь к JAR
2. Каталог **`bsl-language-server/`** в проекте — положите туда файл `bsl-language-server.jar` (см. [bsl-language-server/README.md](bsl-language-server/README.md)); тогда сборка и запуск из корня или из `java/` работают без настройки
3. Файл `bsl-language-server.jar` в текущей директории при запуске

## Сборка и запуск

Всё в каталоге [java/](java/):

```bash
cd java
mvn clean package
java -jar target/mcp-1c-bsl-0.1.0-all.jar
```

Подробнее: [java/README.md](java/README.md).

**Подробная инструкция для macOS:** [docs/MACOS.md](docs/MACOS.md) — установка Java 17, сборка, размещение JAR, настройка Cursor и типичные проблемы.

## Инструменты (Tools)

| Инструмент     | Описание |
|----------------|----------|
| **bsl_analyze** | Анализ каталога или файла: диагностики (ошибки, предупреждения, подсказки) и метрики. Соответствует правилам SonarQube BSL Plugin. |
| **bsl_format**  | Форматирование файла или каталога по правилам BSL LS. |

Архитектура и связка с SonarQube описаны в [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
