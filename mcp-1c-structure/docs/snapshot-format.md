# Формат снимка

Снимок состоит из трёх JSON-файлов (каталог) или одного JSON с полями meta, objects, relations (POST /import).

## meta.json

Поля: version, configName, configVersion, exportedAt, source, objectCount, indexVersion.

Пример:

```json
{
  "version": "1.0",
  "configName": "Пример конфигурации",
  "configVersion": "1.0.0",
  "exportedAt": "2025-02-15T12:00:00Z",
  "source": "example",
  "objectCount": 3,
  "indexVersion": 1
}
```

## objects.json

Массив объектов. Каждый элемент: id, type, name, synonym, props (массив Prop: name, type, synonym), tabularSections (массив: name, props), forms, modules, description.

Пример фрагмента: объект с id doc.РеализацияТоваров, type Document, props с реквизитами Номер и Контрагент, пустые tabularSections, forms и modules.

## relations.json

Массив связей: from, to, kind. Пример: {"from": "doc.РеализацияТоваров", "to": "cat.Контрагенты", "kind": "reference"}.

## Целостность при импорте

При импорте from и to должны присутствовать среди объектов. В БД внешние ключи не создаются.
