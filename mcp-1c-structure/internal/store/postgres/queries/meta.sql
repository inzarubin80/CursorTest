-- name: GetMeta :one
SELECT value FROM meta WHERE key = $1;

-- name: SetMeta :exec
INSERT INTO meta (key, value) VALUES ($1, $2)
ON CONFLICT (key) DO UPDATE SET value = $2;

-- name: ListMeta :many
SELECT key, value FROM meta;
