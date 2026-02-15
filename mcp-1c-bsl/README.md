# MCP-сервер для 1С (BSL)

MCP-сервер для анализа и форматирования кода 1С (BSL/OneScript) через [BSL Language Server](https://github.com/1c-syntax/bsl-language-server). Тот же движок используется в [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community).

**Реализация только на Java** — в организации достаточно одной JVM.

## Требования

- **Java 17+**
- JAR [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases)
- Переменная окружения **`BSL_LANGUAGE_SERVER_JAR`** (или файл `bsl-language-server.jar` в текущей директории)

## Сборка и запуск

Всё в каталоге [java/](java/):

```bash
cd java
mvn clean package
java -jar target/mcp-1c-bsl-0.1.0-all.jar
```

Подробнее: [java/README.md](java/README.md).

## Инструменты (Tools)

| Инструмент     | Описание |
|----------------|----------|
| **bsl_analyze** | Анализ каталога или файла: диагностики (ошибки, предупреждения, подсказки) и метрики. Соответствует правилам SonarQube BSL Plugin. |
| **bsl_format**  | Форматирование файла или каталога по правилам BSL LS. |

Архитектура и связка с SonarQube описаны в [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
