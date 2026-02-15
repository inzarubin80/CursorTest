# MCP-сервер для 1С (BSL) и SonarQube BSL Plugin

## Связка технологий

- **BSL Language Server** (1c-syntax) — один и тот же движок используется и в плагине SonarQube, и при запуске из командной строки.
- **SonarQube 1C (BSL) Community Plugin** — встроенный анализатор = BSL Language Server; плюс поддержка **импорта** отчёта BSL LS в формате JSON.

Поэтому у MCP два сценария:

1. **Только BSL LS (без SonarQube)** — проверка синтаксиса и диагностика «на месте», тот же набор правил, что и в Sonar.
2. **С SonarQube** — либо полный скан через sonar-scanner, либо предварительный запуск BSL LS и передача отчёта в Sonar через `sonar.bsl.languageserver.reportPaths`.

---

## Режим 1: Только BSL Language Server (рекомендуется для MCP)

**Требования:** Java 17+, JAR [BSL Language Server](https://github.com/1c-syntax/bsl-language-server/releases).

BSL LS умеет:

- **Анализ:** `java -jar bsl-language-server.jar --analyze --srcDir <path> --reporter json [-o <outputDir>]`
  - В текущей директории (или в `-o`) создаётся `bsl-json.json` с полем `fileinfos[]`: путь к файлу, `diagnostics[]` (range, severity, code, message), метрики (ncloc, complexity и т.д.).
- **Форматирование:** `java -jar bsl-language-server.jar --format --src <path>` (файл или каталог).

**Идея MCP:**

- Инструмент **`bsl_analyze`**: принимает путь к каталогу (или к одному файлу; для одного файла можно передать каталог с этим файлом или временный каталог). Запускает BSL LS в режиме `--analyze` с `--reporter json`, читает `bsl-json.json`, отдаёт клиенту структурированный список диагностик (и при желании метрик). Так мы получаем проверку синтаксиса и все правила, совместимые с Sonar, без поднятия Sonar.
- Инструмент **`bsl_format`**: принимает путь к файлу или каталогу, вызывает BSL LS `--format --src <path>`, возвращает успех/ошибку (при необходимости можно читать изменённые файлы и отдавать содержимое).

Дополнительно можно добавить инструмент **`bsl_analyze_text`**: сохранить переданный код во временный `.bsl` файл, вызвать `bsl_analyze` для каталога с этим файлом, вернуть диагностики и удалить временный файл — удобно для проверки фрагмента кода из чата.

---

## Режим 2: Интеграция с SonarQube BSL Plugin

**Требования:** установленный SonarQube с плагином [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community), при необходимости — sonar-scanner и `sonar-project.properties`.

Варианты:

### 2a) Классический скан

- В проекте есть `sonar-project.properties` (или параметры передаются в CLI).
- MCP может предлагать инструмент **`sonar_scan`**: запуск `sonar-scanner` в указанной директории (или с переданным путём к проекту). Результат — вывод в консоль; при необходимости позже можно добавить разбор лога или запрос к Sonar Web API (api/issues/search, api/measures/component), если передать URL и токен.

### 2b) Импорт отчёта BSL LS в Sonar (без встроенного анализатора на сервере)

- Запустить BSL LS: `--analyze --srcDir <project_sources> --reporter json -o <outDir>`.
- Получить `bsl-json.json` (или несколько отчётов).
- Запустить sonar-scanner с параметрами:
  - `sonar.bsl.languageserver.reportPaths=<path>/bsl-json.json`
  - `sonar.bsl.languageserver.enabled=false` — чтобы не дублировать анализ на стороне Sonar, а только импортировать готовый отчёт.

MCP может предлагать инструмент **`sonar_import_bsl_report`**: принять путь к проекту и (опционально) путь к каталогу с отчётом; сгенерировать отчёт через BSL LS, затем вызвать sonar-scanner с `reportPaths` и `enabled=false`. Так мы «делаем такой MCP», который связывает BSL LS и Sonar BSL Plugin через импорт отчёта.

---

## Что имеет смысл реализовать в MCP

| Инструмент / возможность | Режим | Описание |
|---------------------------|--------|----------|
| **bsl_analyze** | BSL LS | Запуск BSL LS `--analyze`, разбор JSON, возврат диагностик (и при желании метрик). Проверка синтаксиса и правил. |
| **bsl_format** | BSL LS | Запуск BSL LS `--format` для файла/каталога. |
| **bsl_analyze_text** | BSL LS | Анализ переданной строки кода (временный файл + bsl_analyze). |
| **sonar_scan** | Sonar | Запуск sonar-scanner по пути к проекту (если есть конфиг). |
| **sonar_import_bsl_report** | Sonar + BSL LS | Генерация отчёта BSL LS и запуск sonar-scanner с `reportPaths` и отключённым встроенным BSL-анализатором. |

Ресурсы (Resources) при желании:

- Справочник по формату JSON BSL LS или списку кодов диагностик (статический файл или сгенерированный из документации).

Промпты (Prompts):

- Например: «Проверь код 1С на типичные ошибки» → подсказка вызвать `bsl_analyze` и интерпретировать результат.

---

## Проверка синтаксиса

Синтаксис и стиль проверяются **BSL Language Server**: при запуске `--analyze` в отчёт попадают и синтаксические ошибки (парсер BSL LS), и диагностики правил (в т.ч. те же, что отображаются в Sonar после импорта). Отдельно поднимать Sonar только ради синтаксиса не обязательно — достаточно инструмента `bsl_analyze` (и при необходимости `bsl_analyze_text` для фрагмента кода).

Итого: MCP «такой» делаем за счёт вызова BSL LS из MCP-инструментов; опционально добавляем сценарии с SonarQube (скан и/или импорт отчёта BSL LS), чтобы один и тот же движок (BSL LS) использовался и локально, и в Sonar BSL Plugin.
