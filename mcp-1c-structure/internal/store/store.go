package store

import (
	"context"

	"github.com/ser/mcp-1c-structure/internal/snapshot"
)

type TypeCount struct {
	Type  string
	Count int64
}

type Store interface {
	Search(ctx context.Context, query, typeFilter string, limit, offset int) ([]snapshot.Object, int, error)
	GetObject(ctx context.Context, id string) (snapshot.Object, bool, error)
	FindReferences(ctx context.Context, id, direction, kind string, limit int) (incoming, outgoing []snapshot.Relation, err error)
	ListTypes(ctx context.Context) ([]TypeCount, error)
	Meta(ctx context.Context) (snapshot.Meta, error)
	Import(ctx context.Context, meta snapshot.Meta, objects []snapshot.Object, relations []snapshot.Relation) error
	Close() error
}
