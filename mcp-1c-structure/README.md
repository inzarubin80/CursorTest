# MCP 1C Structure

MCP-сервер для доступа к структуре конфигурации 1С: поиск объектов, карточка объекта, связи (references). Данные хранятся в PostgreSQL; снимок загружается в БД отдельной ручкой (MCP-инструмент или CLI indexer).

## Документация

Подробная документация в каталоге [docs/](docs/):

- [Архитектура](docs/architecture.md) — MCP, Postgres, ручки загрузки, потоки данных
- [Формат снимка](docs/snapshot-format.md) — meta.json, objects.json, relations.json, целостность
- [API инструментов](docs/api-tools.md) — параметры и ответы всех MCP-инструментов, лимиты
- [Indexer](docs/indexer.md) — CLI и HTTP-режим, POST /import, переменные окружения

## Требования

- Go 1.23+
- PostgreSQL (обязателен для запуска MCP)

## Сборка

```bash
go build -o mcp-1c-structure ./cmd/mcp-1c-structure/
go build -o indexer ./cmd/indexer/
```

## Запуск MCP

Сервер работает в режиме stdio. Для работы **обязательно** задать URL подключения к PostgreSQL.

```bash
export MCP_1C_STRUCTURE_DATABASE_URL="postgres://user:pass@localhost:5432/dbname"
./mcp-1c-structure
```

Без `MCP_1C_STRUCTURE_DATABASE_URL` (или `POSTGRES_DSN`) сервер завершится с ошибкой.

### Переменные окружения

| Переменная | Описание |
|------------|----------|
| `MCP_1C_STRUCTURE_DATABASE_URL` | URL подключения к PostgreSQL (или `POSTGRES_DSN`). **Обязателен** для запуска MCP. |
| `MCP_1C_STRUCTURE_SNAPSHOT_DIR` | Путь по умолчанию к каталогу снимка для инструмента `structure_import_snapshot`, если аргумент `snapshotDir` не передан. |

## Загрузка снимка в БД

Данные в БД появляются **только** после загрузки снимка одной из двух ручек:

1. **MCP-инструмент structure_import_snapshot** — передать путь к каталогу с meta.json, objects.json, relations.json (аргумент `snapshotDir` или каталог из `MCP_1C_STRUCTURE_SNAPSHOT_DIR`).
2. **CLI indexer** — для CI или терминала:

```bash
export MCP_1C_STRUCTURE_DATABASE_URL="postgres://..."
./indexer -snapshot ./snapshot
```

3. **HTTP indexer** — сервис принимает снимок по HTTP (удобно для выгрузки из внешних систем):

```bash
export MCP_1C_STRUCTURE_DATABASE_URL="postgres://..."
./indexer -http :8080
```

Тело запроса: `POST /import`, Content-Type: `application/json`:

```json
{
  "meta": { "version": "1.0", "configName": "...", "configVersion": "...", "exportedAt": "...", "source": "...", "objectCount": 0, "indexVersion": 1 },
  "objects": [ { "id": "...", "type": "...", "name": "...", "synonym": "...", "props": [], "tabularSections": [], "forms": [], "modules": [], "description": "" } ],
  "relations": [ { "from": "...", "to": "...", "kind": "..." } ]
}
```

В ответ — JSON с полями `ok`, `objectCount`, `relationsImported`, `configName`, `configVersion`.

Перед первой загрузкой применить миграции: [goose](https://github.com/pressly/goose) `goose -dir migrations postgres "postgres://..." up` или выполнить вручную `migrations/00001_initial.sql`.

## Инструменты (API)

| Инструмент | Описание |
|------------|----------|
| **structure_snapshot_info** | Информация о снимке: configName, configVersion, exportedAt, source, objectCount. |
| **structure_search** | Поиск по имени/синониму (подстрока). Параметры: `query` (обязательный), `type`, `limit`, `offset`. |
| **structure_get_object** | Полное описание объекта по `objectId`. |
| **structure_find_references** | Входящие и исходящие связи. Параметры: `objectId`, `direction` (incoming/outgoing/both), `kind`, `limit`. |
| **structure_list_types** | Список типов метаданных и количество объектов по каждому типу. |
| **structure_import_snapshot** | Загрузить снимок из каталога в БД. Параметр: `snapshotDir` (путь к каталогу с meta.json, objects.json, relations.json). |

Ответы в формате JSON в поле content.

## Подключение в Cursor

В настройках MCP укажите команду и путь к бинарнику; задайте `MCP_1C_STRUCTURE_DATABASE_URL` в окружении процесса Cursor/IDE:

```json
{
  "mcpServers": {
    "1c-structure": {
      "command": "/path/to/mcp-1c-structure",
      "env": {
        "MCP_1C_STRUCTURE_DATABASE_URL": "postgres://..."
      }
    }
  }
}
```

## Формат снимка

- **meta.json** — version, configName, configVersion, exportedAt, source, objectCount, indexVersion.
- **objects.json** — массив объектов: id, type, name, synonym, props, tabularSections, forms, modules, description.
- **relations.json** — массив рёбер: from, to, kind.

Целостность (from/to в relations должны соответствовать объектам) проверяется при импорте в сервисном слое; в БД внешние ключи не используются.
