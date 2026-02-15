# API инструментов MCP

Все инструменты возвращают результат в поле content (текст JSON или сообщение об ошибке). При ошибке выставляется IsError: true.

## structure_snapshot_info

Информация о загруженном снимке. Параметры: нет. Ответ: summary, configName, configVersion, exportedAt, source, objectCount. Если снимок не загружен — текст «Снимок не загружен.»

## structure_search

Поиск по имени и синониму (подстрока). Параметры: query (обязательный), type, limit (по умолчанию 20, макс. 50), offset. Ответ: summary, total, matches — массив объектов с полями id, type, name, synonym.

## structure_get_object

Полное описание объекта по objectId. Параметры: objectId (обязательный). Ответ: summary, object (полная структура), source. При отсутствии объекта — IsError и текст «Объект не найден: …».

## structure_find_references

Входящие и исходящие связи. Параметры: objectId (обязательный), direction (incoming/outgoing/both), kind, limit (по умолчанию 50, макс. 100). Ответ: summary, incoming, outgoing — массивы объектов с полями from, to, kind.

## structure_list_types

Список типов и количество объектов. Параметры: нет. Ответ: summary, types — массив объектов с полями type, count.

## structure_import_snapshot

Загрузить снимок из каталога в БД. Параметры: snapshotDir (если пусто — используется MCP_1C_STRUCTURE_SNAPSHOT_DIR). Ответ при успехе: summary, objectCount, relationsImported, configName, configVersion. При ошибке — IsError и текст в content.
