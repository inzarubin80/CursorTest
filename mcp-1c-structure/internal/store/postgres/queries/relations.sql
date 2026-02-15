-- name: FindIncoming :many
SELECT from_id, to_id, kind FROM relations WHERE to_id = $1
  AND ($2::text = '' OR kind = $2)
LIMIT $3;

-- name: FindOutgoing :many
SELECT from_id, to_id, kind FROM relations WHERE from_id = $1
  AND ($2::text = '' OR kind = $2)
LIMIT $3;

-- name: InsertRelation :exec
INSERT INTO relations (from_id, to_id, kind) VALUES ($1, $2, $3);
