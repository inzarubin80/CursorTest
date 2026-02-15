package main

import (
	"context"
	"log"
	"os"
	"path/filepath"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"github.com/ser/mcp-1c-standards/internal/tools"
)

const name = "mcp-1c-standards"
const version = "0.1.0"

func contentRoot() string {
	if dir := os.Getenv("MCP_1C_STANDARDS_CONTENT"); dir != "" {
		return dir
	}
	if exe, err := os.Executable(); err == nil {
		if d := filepath.Join(filepath.Dir(exe), "content"); dirExists(d) {
			return d
		}
	}
	if cwd, err := os.Getwd(); err == nil {
		if d := filepath.Join(cwd, "content"); dirExists(d) {
			return d
		}
	}
	return ""
}

func dirExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}

func main() {
	tools.SetContentRoot(contentRoot())

	ctx := context.Background()
	server := mcp.NewServer(&mcp.Implementation{Name: name, Version: version}, nil)

	// standards_doc_comment — правила и пример документирующего комментария
	mcp.AddTool(server, &mcp.Tool{
		Name:        "standards_doc_comment",
		Description: "Возвращает правила и пример оформления документирующего комментария к процедуре/функции 1С (BSL) по стандарту v8std и EDT.",
	}, tools.DocComment)

	// standards_lookup — поиск раздела стандарта по ключевому слову или id
	mcp.AddTool(server, &mcp.Tool{
		Name:        "standards_lookup",
		Description: "Поиск по разделам стандартов 1С (v8std, EDT): краткое описание и ссылка. Параметр topic — id раздела (453, 641, 647, 783, … или doc-comment, naming, formatting) или ключевое слово; для id с localFile возвращается полный текст стандарта.",
	}, tools.Lookup)

	// standards_check_comment — проверка текста документирующего комментария
	mcp.AddTool(server, &mcp.Tool{
		Name:        "standards_check_comment",
		Description: "Проверяет текст документирующего комментария BSL на соответствие стандарту: наличие Параметры:, Возвращаемое значение:, формат строк параметров (Имя - Тип - Описание).",
	}, tools.CheckComment)

	if err := server.Run(ctx, &mcp.StdioTransport{}); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
