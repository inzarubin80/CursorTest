-- name: GetObject :one
SELECT id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description
FROM objects WHERE id = $1;

-- name: SearchObjects :many
SELECT id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description
FROM objects
WHERE ($1::text = '' OR LOWER(name) LIKE '%' || LOWER($1) || '%' OR LOWER(synonym) LIKE '%' || LOWER($1) || '%')
  AND ($2::text = '' OR LOWER(type) = LOWER($2))
ORDER BY name
LIMIT $3 OFFSET $4;

-- name: SearchObjectsCount :one
SELECT COUNT(*)
FROM objects
WHERE ($1::text = '' OR LOWER(name) LIKE '%' || LOWER($1) || '%' OR LOWER(synonym) LIKE '%' || LOWER($1) || '%')
  AND ($2::text = '' OR LOWER(type) = LOWER($2));

-- name: InsertObject :exec
INSERT INTO objects (id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
ON CONFLICT (id) DO UPDATE SET
  type = $2, name = $3, synonym = $4, props_json = $5, tabular_sections_json = $6, forms = $7, modules = $8, description = $9;

-- name: ObjectExists :one
SELECT EXISTS(SELECT 1 FROM objects WHERE id = $1);

-- name: ListTypes :many
SELECT type, COUNT(*)::bigint AS count FROM objects GROUP BY type ORDER BY type;
