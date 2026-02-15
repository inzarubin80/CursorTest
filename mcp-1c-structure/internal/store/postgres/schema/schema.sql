CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE objects (
    id                    TEXT PRIMARY KEY,
    type                  TEXT NOT NULL,
    name                  TEXT NOT NULL,
    synonym               TEXT NOT NULL DEFAULT '',
    props_json            TEXT NOT NULL DEFAULT '[]',
    tabular_sections_json TEXT NOT NULL DEFAULT '[]',
    forms                 TEXT NOT NULL DEFAULT '[]',
    modules               TEXT NOT NULL DEFAULT '[]',
    description           TEXT NOT NULL DEFAULT ''
);

CREATE TABLE relations (
    from_id TEXT NOT NULL,
    to_id   TEXT NOT NULL,
    kind    TEXT NOT NULL DEFAULT ''
);
