package postgres

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/ser/mcp-1c-structure/internal/snapshot"
)

// Import writes meta, objects, and relations into the database. Relations are only inserted if from_id and to_id exist in objects (service-level integrity).
func (p *postgresStore) Import(ctx context.Context, meta snapshot.Meta, objects []snapshot.Object, relations []snapshot.Relation) error {
	objectIDs := make(map[string]bool)
	for i := range objects {
		objectIDs[objects[i].ID] = true
		objectIDs[normalizeID(objects[i].ID)] = true
	}
	// meta
	if err := p.setMeta(ctx, "configName", meta.ConfigName); err != nil {
		return err
	}
	if err := p.setMeta(ctx, "configVersion", meta.ConfigVersion); err != nil {
		return err
	}
	if err := p.setMeta(ctx, "exportedAt", meta.ExportedAt); err != nil {
		return err
	}
	if err := p.setMeta(ctx, "source", meta.Source); err != nil {
		return err
	}
	if err := p.setMeta(ctx, "objectCount", fmt.Sprintf("%d", len(objects))); err != nil {
		return err
	}
	// objects
	for i := range objects {
		o := &objects[i]
		propsJSON, _ := json.Marshal(o.Props)
		tabJSON, _ := json.Marshal(o.TabularSections)
		formsJSON, _ := json.Marshal(o.Forms)
		modsJSON, _ := json.Marshal(o.Modules)
		_, err := p.pool.Exec(ctx,
			`INSERT INTO objects (id, type, name, synonym, props_json, tabular_sections_json, forms, modules, description)
			 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			 ON CONFLICT (id) DO UPDATE SET type=$2, name=$3, synonym=$4, props_json=$5, tabular_sections_json=$6, forms=$7, modules=$8, description=$9`,
			o.ID, o.Type, o.Name, o.Synonym, string(propsJSON), string(tabJSON), string(formsJSON), string(modsJSON), o.Description)
		if err != nil {
			return fmt.Errorf("insert object %s: %w", o.ID, err)
		}
	}
	// relations (only if both ends exist)
	for i := range relations {
		r := &relations[i]
		from := normalizeID(r.From)
		to := normalizeID(r.To)
		if !objectIDs[from] && !objectIDs[r.From] {
			continue
		}
		if !objectIDs[to] && !objectIDs[r.To] {
			continue
		}
		_, err := p.pool.Exec(ctx, `INSERT INTO relations (from_id, to_id, kind) VALUES ($1, $2, $3)`, r.From, r.To, r.Kind)
		if err != nil {
			return fmt.Errorf("insert relation %s -> %s: %w", r.From, r.To, err)
		}
	}
	return nil
}

func (p *postgresStore) setMeta(ctx context.Context, key, value string) error {
	_, err := p.pool.Exec(ctx, `INSERT INTO meta (key, value) VALUES ($1, $2) ON CONFLICT (key) DO UPDATE SET value = $2`, key, value)
	return err
}
