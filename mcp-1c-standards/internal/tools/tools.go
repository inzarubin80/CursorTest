package tools

import (
	"context"
	"embed"
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

//go:embed content/*
var contentFS embed.FS

// contentRoot задаётся при старте из main: каталог content/ в проекте (рядом с бинарником или по MCP_1C_STANDARDS_CONTENT).
// Если пусто или файлы не найдены — используется встроенный контент (embed).
var contentRoot string

// SetContentRoot задаёт каталог, откуда читать контент (doc-comment.md, lookup.json). Сервис не обращается к сети.
func SetContentRoot(root string) { contentRoot = filepath.Clean(root) }

// readContent читает файл: сначала из каталога проекта (contentRoot), иначе из встроенного контента.
func readContent(name string) ([]byte, error) {
	if contentRoot != "" {
		p := filepath.Join(contentRoot, name)
		data, err := os.ReadFile(p)
		if err == nil {
			return data, nil
		}
	}
	return contentFS.ReadFile("content/" + name)
}

type DocCommentParams struct {
	Topic string `json:"topic" jsonschema:"description=Тема: параметры, возвращаемое значение, пример, структура (опционально)"`
}

type LookupParams struct {
	Topic string `json:"topic" jsonschema:"description=ID раздела (453, 641, doc-comment, naming, formatting) или ключевое слово для поиска"`
}

type CheckCommentParams struct {
	CommentText string `json:"commentText" jsonschema:"description=Текст документирующего комментария для проверки"`
}

type lookupData struct {
	Sections    []section    `json:"sections"`
	Diagnostics []diagnostic `json:"diagnostics"`
}

type section struct {
	ID         string `json:"id"`
	Title      string `json:"title"`
	Summary    string `json:"summary"`
	URL        string `json:"url"`
	LocalFile  string `json:"localFile"` // полный текст стандарта в проекте (std-453.md и т.д.)
}

type diagnostic struct {
	Code  string `json:"code"`
	Title string `json:"title"`
	Fix   string `json:"fix"`
}

func DocComment(ctx context.Context, req *mcp.CallToolRequest, args DocCommentParams) (*mcp.CallToolResult, any, error) {
	data, err := readContent("doc-comment.md")
	if err != nil {
		return &mcp.CallToolResult{
			Content: []mcp.Content{&mcp.TextContent{Text: "Ошибка чтения контента: " + err.Error()}},
			IsError: true,
		}, nil, nil
	}
	text := string(data)
	if args.Topic != "" {
		// Можно сузить по подразделу (простейший фильтр по заголовкам)
		lower := strings.ToLower(args.Topic)
		if strings.Contains(lower, "параметр") || strings.Contains(lower, "пример") {
			// Отдаём весь документ — в нём есть и параметры, и примеры
		}
	}
	return &mcp.CallToolResult{
		Content: []mcp.Content{&mcp.TextContent{Text: text}},
	}, nil, nil
}

func Lookup(ctx context.Context, req *mcp.CallToolRequest, args LookupParams) (*mcp.CallToolResult, any, error) {
	data, err := readContent("lookup.json")
	if err != nil {
		return &mcp.CallToolResult{
			Content: []mcp.Content{&mcp.TextContent{Text: "Ошибка чтения контента: " + err.Error()}},
			IsError: true,
		}, nil, nil
	}
	var ld lookupData
	if err := json.Unmarshal(data, &ld); err != nil {
		return &mcp.CallToolResult{
			Content: []mcp.Content{&mcp.TextContent{Text: "Ошибка разбора JSON: " + err.Error()}},
			IsError: true,
		}, nil, nil
	}
	topic := strings.TrimSpace(strings.ToLower(args.Topic))
	var out strings.Builder

	// Поиск по разделам
	for _, s := range ld.Sections {
		if topic == "" || strings.Contains(strings.ToLower(s.ID), topic) ||
			strings.Contains(strings.ToLower(s.Title), topic) ||
			strings.Contains(strings.ToLower(s.Summary), topic) {
			out.WriteString("## " + s.Title + " (id: " + s.ID + ")\n")
			out.WriteString(s.Summary + "\n")
			out.WriteString("Ссылка: " + s.URL + "\n\n")
			// Если в проекте есть полный текст стандарта — подставляем его (сервис в сеть не ходит)
			if s.LocalFile != "" {
				if full, err := readContent(s.LocalFile); err == nil {
					out.WriteString("---\n\n")
					out.WriteString(string(full))
					out.WriteString("\n\n")
				}
			}
		}
	}
	// Поиск по коду диагностики
	for _, d := range ld.Diagnostics {
		if topic != "" && strings.Contains(strings.ToLower(d.Code), topic) {
			out.WriteString("## Диагностика: " + d.Code + " — " + d.Title + "\n")
			out.WriteString("Как исправить: " + d.Fix + "\n\n")
		}
	}
	if out.Len() == 0 && topic != "" {
		out.WriteString("Разделы по запросу «" + args.Topic + "» не найдены. Доступные id: 453, 641, doc-comment, naming, formatting. Для диагностик: MissingSpace, FunctionOutParameter, LineLength.\n")
	} else if out.Len() == 0 {
		out.WriteString("Укажите topic — id раздела или ключевое слово (например: 453, параметры, doc-comment).\n")
	}
	return &mcp.CallToolResult{
		Content: []mcp.Content{&mcp.TextContent{Text: out.String()}},
	}, nil, nil
}

func CheckComment(ctx context.Context, req *mcp.CallToolRequest, args CheckCommentParams) (*mcp.CallToolResult, any, error) {
	text := strings.TrimSpace(args.CommentText)
	if text == "" {
		return &mcp.CallToolResult{
			Content: []mcp.Content{&mcp.TextContent{Text: "Передайте commentText — текст документирующего комментария для проверки."}},
			IsError: true,
		}, nil, nil
	}
	var issues []string

	// Проверка наличия "Параметры:" если есть параметры в описании (упрощённо: если есть " - " в строках)
	hasParamSection := regexp.MustCompile(`(?m)^\s*//\s*Параметры\s*:`).MatchString(text)
	// Строка параметра: //   Имя - Тип - Описание
	paramLine := regexp.MustCompile(`(?m)^\s*//\s+\S+\s+-\s+\S+\s+-\s+.+`).MatchString(text)
	if paramLine && !hasParamSection {
		issues = append(issues, "Обнаружены строки, похожие на описание параметров, но отсутствует ключевое слово «Параметры:» (с двоеточием).")
	}
	if hasParamSection {
		// Проверка формата: после Параметры: должны быть строки вида "//   Имя - Тип - Описание"
		afterParams := regexp.MustCompile(`(?s)Параметры\s*:\s*(.*?)(?://\s*Возвращаемое|\z)`).FindStringSubmatch(text)
		if len(afterParams) > 1 {
			lines := strings.Split(afterParams[1], "\n")
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if line == "" || !strings.HasPrefix(line, "//") {
					continue
				}
				inner := strings.TrimPrefix(line, "//")
				inner = strings.TrimSpace(inner)
				if inner == "" {
					continue
				}
				parts := regexp.MustCompile(`\s+-\s+`).Split(inner, 3)
				if len(parts) < 3 {
					issues = append(issues, "Строка параметра должна быть в формате «Имя - Тип - Описание»: "+inner)
				}
			}
		}
	}

	// Возвращаемое значение для функций — опционально упоминаем
	hasReturn := regexp.MustCompile(`(?m)^\s*//\s*Возвращаемое значение\s*:`).MatchString(text)
	if len(issues) == 0 && !hasParamSection && !hasReturn && len(text) > 20 {
		issues = append(issues, "Рекомендуется добавить блоки «Параметры:» и (для функций) «Возвращаемое значение:» по стандарту v8std.")
	}

	var result string
	if len(issues) == 0 {
		result = "Замечаний не найдено. Комментарий соответствует базовым правилам оформления (Параметры:, формат строк параметров)."
	} else {
		result = "Обнаружены замечания:\n"
		for _, i := range issues {
			result += "- " + i + "\n"
		}
		result += "\nСм. правила: standards_doc_comment или standards_lookup (topic: 453)."
	}
	return &mcp.CallToolResult{
		Content: []mcp.Content{&mcp.TextContent{Text: result}},
	}, nil, nil
}
