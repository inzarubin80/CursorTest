package main

import (
	"context"
	"errors"

	"github.com/ser/mcp-1c-structure/internal/snapshot"
	"github.com/ser/mcp-1c-structure/internal/store"
	"github.com/ser/mcp-1c-structure/internal/store/postgres"
)

func initStore(dbURL string) (store.Store, snapshot.Meta, error) {
	if dbURL == "" {
		return nil, snapshot.Meta{}, errors.New("MCP_1C_STRUCTURE_DATABASE_URL or POSTGRES_DSN required")
	}
	s, err := postgres.New(dbURL)
	if err != nil {
		return nil, snapshot.Meta{}, err
	}
	meta, err := s.Meta(context.Background())
	if err != nil {
		_ = s.Close()
		return nil, snapshot.Meta{}, err
	}
	return s, meta, nil
}
