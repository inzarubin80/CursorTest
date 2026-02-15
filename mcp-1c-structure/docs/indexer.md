# Indexer

Утилита загрузки снимка структуры в PostgreSQL. Два режима: CLI (чтение из каталога) и HTTP-сервер (приём JSON по POST).

## Требования

- Переменная окружения **MCP_1C_STRUCTURE_DATABASE_URL** или **POSTGRES_DSN** — обязательна. Без неё indexer завершается с ошибкой.

## Режимы запуска

### 1. CLI — загрузка из каталога

Читает снимок из указанного каталога (meta.json, objects.json, relations.json) и один раз импортирует в БД, затем завершает работу.

```bash
export MCP_1C_STRUCTURE_DATABASE_URL="postgres://user:pass@localhost:5432/dbname"
./indexer -snapshot ./snapshot
```

**Флаг -snapshot:** путь к каталогу снимка. Если не указан, подставляется значение MCP_1C_STRUCTURE_SNAPSHOT_DIR; если и оно пусто — `snapshot` (относительно текущей директории).

### 2. HTTP-сервер — приём снимка по HTTP

Запуск сервера на указанном адресе; снимок передаётся в теле POST-запроса.

```bash
export MCP_1C_STRUCTURE_DATABASE_URL="postgres://..."
./indexer -http :8080
```

**Флаг -http:** адрес слушать (например `:8080`, `127.0.0.1:8080`). При указании -http режим CLI не используется.

## HTTP API

### GET /

Краткая подсказка в виде текста: «POST /import with JSON body: …».

### POST /import

Принимает снимок в теле запроса и импортирует в БД.

**Заголовки:** `Content-Type: application/json`.

**Тело:** JSON-объект с полями:

| Поле | Тип | Описание |
|------|-----|----------|
| meta | object | Метаданные снимка (см. [Формат снимка](snapshot-format.md)#metajson). |
| objects | array | Массив объектов метаданных. |
| relations | array | Массив связей (from, to, kind). |

**Успех (200):** в ответе JSON с полями `ok` (true), `objectCount`, `relationsImported`, `configName`, `configVersion`.

**Ошибки:**

- **400 Bad Request** — невалидный JSON в теле (текст в теле ответа).
- **405 Method Not Allowed** — метод не POST (разрешён только POST).
- **500 Internal Server Error** — ошибка импорта в БД (текст «import failed: …»).

## Переменные окружения

| Переменная | Описание |
|------------|----------|
| MCP_1C_STRUCTURE_DATABASE_URL | URL подключения к PostgreSQL (обязательна). |
| POSTGRES_DSN | Альтернатива MCP_1C_STRUCTURE_DATABASE_URL. |
| MCP_1C_STRUCTURE_SNAPSHOT_DIR | Каталог снимка по умолчанию для режима CLI (флаг -snapshot не указан). |

## Миграции

Перед первой загрузкой нужно применить миграции к БД: [goose](https://github.com/pressly/goose) `goose -dir migrations postgres "postgres://..." up` или выполнить SQL из `migrations/00001_initial.sql` вручную.
