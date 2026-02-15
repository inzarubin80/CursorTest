-- +goose Up
-- meta: key-value for snapshot metadata (no FK)
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- objects: metadata objects (no FK)
CREATE TABLE IF NOT EXISTS objects (
    id                   TEXT PRIMARY KEY,
    type                 TEXT NOT NULL,
    name                 TEXT NOT NULL,
    synonym              TEXT NOT NULL DEFAULT '',
    props_json           TEXT NOT NULL DEFAULT '[]',
    tabular_sections_json TEXT NOT NULL DEFAULT '[]',
    forms                TEXT NOT NULL DEFAULT '[]',
    modules              TEXT NOT NULL DEFAULT '[]',
    description          TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_objects_type ON objects(type);
CREATE INDEX IF NOT EXISTS idx_objects_name_lower ON objects(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_objects_synonym_lower ON objects(LOWER(synonym));

-- pg_trgm for ILIKE substring search
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_objects_name_trgm ON objects USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_objects_synonym_trgm ON objects USING gin (synonym gin_trgm_ops);

-- relations: edges (no FK; integrity enforced in service)
CREATE TABLE IF NOT EXISTS relations (
    from_id TEXT NOT NULL,
    to_id   TEXT NOT NULL,
    kind    TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_relations_from ON relations(from_id);
CREATE INDEX IF NOT EXISTS idx_relations_to ON relations(to_id);

-- +goose Down
DROP TABLE IF EXISTS relations;
DROP TABLE IF EXISTS objects;
DROP TABLE IF EXISTS meta;
