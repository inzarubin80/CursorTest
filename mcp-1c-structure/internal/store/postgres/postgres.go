package postgres

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/ser/mcp-1c-structure/internal/snapshot"
	"github.com/ser/mcp-1c-structure/internal/store"
)

type postgresStore struct {
	pool *pgxpool.Pool
}

func New(dbURL string) (store.Store, error) {
	if dbURL == "" {
		return nil, fmt.Errorf("database URL is empty")
	}
	pool, err := pgxpool.New(context.Background(), dbURL)
	if err != nil {
		return nil, err
	}
	if err := pool.Ping(context.Background()); err != nil {
		pool.Close()
		return nil, err
	}
	return &postgresStore{pool: pool}, nil
}

func (p *postgresStore) Search(ctx context.Context, query, typeFilter string, limit, offset int) ([]snapshot.Object, int, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}
	query = strings.TrimSpace(strings.ToLower(query))
	typeFilter = strings.TrimSpace(strings.ToLower(typeFilter))
	likeQ := "%" + query + "%"
	var total int
	err := p.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM objects WHERE ($1 = '' OR LOWER(name) LIKE $2 OR LOWER(synonym) LIKE $2) AND ($3 = '' OR LOWER(type) = $3)`,
		query, likeQ, typeFilter).Scan(&total)
	if err != nil {
		return nil, 0, err
	}
	rows, err := p.pool.Query(ctx,
		`SELECT id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description
		 FROM objects WHERE ($1 = '' OR LOWER(name) LIKE $2 OR LOWER(synonym) LIKE $2) AND ($3 = '' OR LOWER(type) = $3)
		 ORDER BY name LIMIT $4 OFFSET $5`,
		query, likeQ, typeFilter, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var list []snapshot.Object
	for rows.Next() {
		var o snapshot.Object
		var propsJSON, tabSecJSON, formsJSON, modsJSON string
		err := rows.Scan(&o.ID, &o.Type, &o.Name, &o.Synonym, &propsJSON, &tabSecJSON, &formsJSON, &modsJSON, &o.Description)
		if err != nil {
			return nil, 0, err
		}
		_ = json.Unmarshal([]byte(propsJSON), &o.Props)
		_ = json.Unmarshal([]byte(tabSecJSON), &o.TabularSections)
		_ = json.Unmarshal([]byte(formsJSON), &o.Forms)
		_ = json.Unmarshal([]byte(modsJSON), &o.Modules)
		list = append(list, o)
	}
	return list, total, rows.Err()
}

func (p *postgresStore) GetObject(ctx context.Context, id string) (snapshot.Object, bool, error) {
	id = normalizeID(id)
	var o snapshot.Object
	var propsJSON, tabSecJSON, formsJSON, modsJSON string
	err := p.pool.QueryRow(ctx,
		`SELECT id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description FROM objects WHERE id = $1`,
		id).Scan(&o.ID, &o.Type, &o.Name, &o.Synonym, &propsJSON, &tabSecJSON, &formsJSON, &modsJSON, &o.Description)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return snapshot.Object{}, false, nil
		}
		return snapshot.Object{}, false, err
	}
	_ = json.Unmarshal([]byte(propsJSON), &o.Props)
	_ = json.Unmarshal([]byte(tabSecJSON), &o.TabularSections)
	_ = json.Unmarshal([]byte(formsJSON), &o.Forms)
	_ = json.Unmarshal([]byte(modsJSON), &o.Modules)
	return o, true, nil
}

func (p *postgresStore) FindReferences(ctx context.Context, id, direction, kind string, limit int) (incoming, outgoing []snapshot.Relation, err error) {
	id = normalizeID(id)
	if limit <= 0 {
		limit = 50
	}
	if limit > 100 {
		limit = 100
	}
	wantIn := direction == "incoming" || direction == "both" || direction == ""
	wantOut := direction == "outgoing" || direction == "both" || direction == ""
	if wantIn {
		rows, e := p.pool.Query(ctx, `SELECT from_id, to_id, kind FROM relations WHERE to_id = $1 AND ($2 = '' OR kind = $2) LIMIT $3`, id, kind, limit)
		if e != nil {
			return nil, nil, e
		}
		for rows.Next() {
			var r snapshot.Relation
			_ = rows.Scan(&r.From, &r.To, &r.Kind)
			incoming = append(incoming, r)
		}
		rows.Close()
	}
	if wantOut {
		rows, e := p.pool.Query(ctx, `SELECT from_id, to_id, kind FROM relations WHERE from_id = $1 AND ($2 = '' OR kind = $2) LIMIT $3`, id, kind, limit)
		if e != nil {
			return nil, nil, e
		}
		for rows.Next() {
			var r snapshot.Relation
			_ = rows.Scan(&r.From, &r.To, &r.Kind)
			outgoing = append(outgoing, r)
		}
		rows.Close()
	}
	return incoming, outgoing, nil
}

func (p *postgresStore) ListTypes(ctx context.Context) ([]store.TypeCount, error) {
	rows, err := p.pool.Query(ctx, `SELECT type, COUNT(*)::bigint FROM objects GROUP BY type ORDER BY type`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []store.TypeCount
	for rows.Next() {
		var t store.TypeCount
		_ = rows.Scan(&t.Type, &t.Count)
		out = append(out, t)
	}
	return out, rows.Err()
}

func (p *postgresStore) Meta(ctx context.Context) (snapshot.Meta, error) {
	keys := []string{"configName", "configVersion", "exportedAt", "source", "objectCount"}
	m := snapshot.Meta{}
	for _, k := range keys {
		var v string
		err := p.pool.QueryRow(ctx, `SELECT value FROM meta WHERE key = $1`, k).Scan(&v)
		if err != nil {
			continue
		}
		switch k {
		case "configName":
			m.ConfigName = v
		case "configVersion":
			m.ConfigVersion = v
		case "exportedAt":
			m.ExportedAt = v
		case "source":
			m.Source = v
		case "objectCount":
			fmt.Sscanf(v, "%d", &m.ObjectCount)
		}
	}
	return m, nil
}

func (p *postgresStore) Close() error {
	if p.pool != nil {
		p.pool.Close()
	}
	return nil
}

func normalizeID(id string) string {
	id = strings.TrimSpace(id)
	if idx := strings.Index(id, "."); idx > 0 {
		prefix := strings.ToLower(id[:idx])
		name := id[idx+1:]
		short := map[string]string{
			"document": "doc", "catalog": "cat", "commonmodule": "commonmodule",
			"report": "report", "dataprocessor": "dataprocessor",
		}
		if s, ok := short[prefix]; ok {
			return s + "." + name
		}
		return prefix + "." + name
	}
	return id
}
