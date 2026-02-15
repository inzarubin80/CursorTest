package tools

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"github.com/ser/mcp-1c-structure/internal/config"
	"github.com/ser/mcp-1c-structure/internal/snapshot"
	"github.com/ser/mcp-1c-structure/internal/store"
)

var currentStore store.Store
var currentMeta snapshot.Meta

func SetStore(s store.Store, m snapshot.Meta) {
	currentStore = s
	currentMeta = m
}

const defaultLimit = 20
const maxLimit = 50

type SnapshotInfoParams struct{}

func SnapshotInfo(ctx context.Context, req *mcp.CallToolRequest, args SnapshotInfoParams) (*mcp.CallToolResult, any, error) {
	meta := currentMeta
	if currentStore != nil {
		var err error
		meta, err = currentStore.Meta(ctx)
		if err != nil {
			return errResult("Meta: " + err.Error()), nil, nil
		}
	}
	if meta.ConfigName == "" && meta.Source == "" {
		return &mcp.CallToolResult{
			Content: []mcp.Content{&mcp.TextContent{Text: "Снимок не загружен."}},
		}, nil, nil
	}
	summary := fmt.Sprintf("Снимок %s %s, %d объектов, выгрузка от %s.", meta.ConfigName, meta.ConfigVersion, meta.ObjectCount, meta.ExportedAt)
	out := map[string]any{
		"summary": summary, "configName": meta.ConfigName, "configVersion": meta.ConfigVersion,
		"exportedAt": meta.ExportedAt, "source": meta.Source, "objectCount": meta.ObjectCount,
	}
	return jsonResult(out), nil, nil
}

type SearchParams struct {
	Query  string `json:"query"`
	Type   string `json:"type"`
	Limit  int    `json:"limit"`
	Offset int    `json:"offset"`
}

func Search(ctx context.Context, req *mcp.CallToolRequest, args SearchParams) (*mcp.CallToolResult, any, error) {
	if currentStore == nil {
		return errResult("хранилище не инициализировано"), nil, nil
	}
	if args.Query == "" {
		return errResult("query обязателен"), nil, nil
	}
	if args.Limit <= 0 {
		args.Limit = defaultLimit
	}
	if args.Limit > maxLimit {
		args.Limit = maxLimit
	}
	objects, total, err := currentStore.Search(ctx, args.Query, args.Type, args.Limit, args.Offset)
	if err != nil {
		return errResult(err.Error()), nil, nil
	}
	matches := make([]map[string]string, len(objects))
	for i := range objects {
		matches[i] = map[string]string{
			"id": objects[i].ID, "type": objects[i].Type,
			"name": objects[i].Name, "synonym": objects[i].Synonym,
		}
	}
	out := map[string]any{"summary": fmt.Sprintf("Найдено %d объектов.", total), "total": total, "matches": matches}
	return jsonResult(out), nil, nil
}

type GetObjectParams struct {
	ObjectID string `json:"objectId"`
}

func GetObject(ctx context.Context, req *mcp.CallToolRequest, args GetObjectParams) (*mcp.CallToolResult, any, error) {
	if currentStore == nil {
		return errResult("хранилище не инициализировано"), nil, nil
	}
	if args.ObjectID == "" {
		return errResult("objectId обязателен"), nil, nil
	}
	obj, ok, err := currentStore.GetObject(ctx, args.ObjectID)
	if err != nil {
		return errResult(err.Error()), nil, nil
	}
	if !ok {
		return errResult("Объект не найден: " + args.ObjectID), nil, nil
	}
	out := map[string]any{"summary": "Объект " + obj.Name + ".", "object": obj, "source": "snapshot/objects.json"}
	return jsonResult(out), nil, nil
}

type FindReferencesParams struct {
	ObjectID  string `json:"objectId"`
	Direction string `json:"direction"`
	Kind      string `json:"kind"`
	Limit     int    `json:"limit"`
}

func FindReferences(ctx context.Context, req *mcp.CallToolRequest, args FindReferencesParams) (*mcp.CallToolResult, any, error) {
	if currentStore == nil {
		return errResult("хранилище не инициализировано"), nil, nil
	}
	if args.ObjectID == "" {
		return errResult("objectId обязателен"), nil, nil
	}
	if args.Limit <= 0 {
		args.Limit = 50
	}
	if args.Limit > 100 {
		args.Limit = 100
	}
	incoming, outgoing, err := currentStore.FindReferences(ctx, args.ObjectID, args.Direction, args.Kind, args.Limit)
	if err != nil {
		return errResult(err.Error()), nil, nil
	}
	inMaps := make([]map[string]string, len(incoming))
	for i := range incoming {
		inMaps[i] = map[string]string{"from": incoming[i].From, "to": incoming[i].To, "kind": incoming[i].Kind}
	}
	outMaps := make([]map[string]string, len(outgoing))
	for i := range outgoing {
		outMaps[i] = map[string]string{"from": outgoing[i].From, "to": outgoing[i].To, "kind": outgoing[i].Kind}
	}
	out := map[string]any{
		"summary":  fmt.Sprintf("Входящих: %d, исходящих: %d.", len(incoming), len(outgoing)),
		"incoming": inMaps, "outgoing": outMaps,
	}
	return jsonResult(out), nil, nil
}

type ListTypesParams struct{}

func ListTypes(ctx context.Context, req *mcp.CallToolRequest, args ListTypesParams) (*mcp.CallToolResult, any, error) {
	if currentStore == nil {
		return errResult("хранилище не инициализировано"), nil, nil
	}
	types, err := currentStore.ListTypes(ctx)
	if err != nil {
		return errResult(err.Error()), nil, nil
	}
	typeRows := make([]map[string]any, len(types))
	for i := range types {
		typeRows[i] = map[string]any{"type": types[i].Type, "count": types[i].Count}
	}
	out := map[string]any{"summary": "Типы метаданных в снимке.", "types": typeRows}
	return jsonResult(out), nil, nil
}

type ImportSnapshotParams struct {
	SnapshotDir string `json:"snapshotDir"`
}

func ImportSnapshot(ctx context.Context, req *mcp.CallToolRequest, args ImportSnapshotParams) (*mcp.CallToolResult, any, error) {
	if currentStore == nil {
		return errResult("хранилище не инициализировано"), nil, nil
	}
	dir := args.SnapshotDir
	if dir == "" {
		dir = config.SnapshotDir()
	}
	if dir == "" {
		return errResult("snapshotDir обязателен или задайте MCP_1C_STRUCTURE_SNAPSHOT_DIR"), nil, nil
	}
	meta, objects, relations, err := snapshot.LoadSnapshot(dir)
	if err != nil {
		return errResult("LoadSnapshot: " + err.Error()), nil, nil
	}
	if err := currentStore.Import(ctx, meta, objects, relations); err != nil {
		return errResult("Import: " + err.Error()), nil, nil
	}
	summary := fmt.Sprintf("Импорт завершён: %s %s, объектов %d, связей %d.", meta.ConfigName, meta.ConfigVersion, len(objects), len(relations))
	out := map[string]any{
		"summary":           summary,
		"objectCount":       len(objects),
		"relationsImported": len(relations),
		"configName":        meta.ConfigName,
		"configVersion":     meta.ConfigVersion,
	}
	return jsonResult(out), nil, nil
}

func jsonResult(v any) *mcp.CallToolResult {
	data, _ := json.Marshal(v)
	return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: string(data)}}}
}

func errResult(msg string) *mcp.CallToolResult {
	return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: msg}}, IsError: true}
}
