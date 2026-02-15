package main

import (
	"context"
	"log"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"github.com/ser/mcp-1c-structure/internal/config"
	"github.com/ser/mcp-1c-structure/internal/tools"
)

const name = "mcp-1c-structure"
const version = "0.1.0"

func main() {
	dbURL := config.DatabaseURL()
	st, meta, err := initStore(dbURL)
	if err != nil {
		log.Fatalf("Init store: %v", err)
	}
	tools.SetStore(st, meta)

	ctx := context.Background()
	server := mcp.NewServer(&mcp.Implementation{Name: name, Version: version}, nil)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_snapshot_info",
		Description: "Информация о загруженном снимке структуры конфигурации 1С: имя, версия, дата выгрузки, число объектов.",
	}, tools.SnapshotInfo)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_search",
		Description: "Поиск объектов по имени/синониму (подстрока). Параметры: query (обязательный), type, limit, offset.",
	}, tools.Search)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_get_object",
		Description: "Полное описание объекта по идентификатору (objectId).",
	}, tools.GetObject)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_find_references",
		Description: "Входящие и исходящие связи объекта. Параметры: objectId, direction (incoming/outgoing/both), kind, limit.",
	}, tools.FindReferences)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_list_types",
		Description: "Список типов метаданных в снимке и количество объектов по каждому типу.",
	}, tools.ListTypes)

	mcp.AddTool(server, &mcp.Tool{
		Name:        "structure_import_snapshot",
		Description: "Загрузить снимок структуры из каталога (meta.json, objects.json, relations.json) в базу данных. Параметр: snapshotDir — путь к каталогу.",
	}, tools.ImportSnapshot)

	if err := server.Run(ctx, &mcp.StdioTransport{}); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
