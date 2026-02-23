package ru.mcp.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import ru.mcp.structure.snapshot.Meta;
import ru.mcp.structure.snapshot.RagZipLoader;
import ru.mcp.structure.snapshot.SnapshotLoader;
import ru.mcp.structure.snapshot.StructureObject;
import ru.mcp.structure.store.InMemoryStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * MCP-сервер для доступа к структуре конфигурации 1С: поиск объектов (нечёткий), карточка объекта.
 * Данные загружаются из файла/каталога при первом обращении и хранятся в памяти. Без PostgreSQL.
 */
public final class Mcp1cStructureServer {

    private static final String NAME = "mcp-1c-structure";
    private static final String VERSION = "0.1.0";
    private static final String MCP_ENDPOINT = "/mcp";
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        try {
            int httpPort = parseHttpPort(args);
            String zipPath = parseZipPath(args);
            McpJsonMapper jsonMapper = getJsonMapper();
            InMemoryStore store = new InMemoryStore();

            if (httpPort > 0) {
                runHttpMode(jsonMapper, store, zipPath, httpPort);
            } else {
                runStdioMode(jsonMapper, store, zipPath);
            }
        } catch (Throwable t) {
            System.err.println("MCP 1C Structure failed to start: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int parseHttpPort(String[] args) {
        String env = System.getenv("MCP_HTTP_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) { }
        }
        for (int i = 0; i < args.length; i++) {
            if ("--http".equals(args[i])) {
                if (i + 1 < args.length && "--port".equals(args[i + 1]) && i + 2 < args.length) {
                    try {
                        return Integer.parseInt(args[i + 2]);
                    } catch (NumberFormatException ignored) { }
                }
                return DEFAULT_HTTP_PORT;
            }
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }

    private static String parseZipPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--zip-path".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        String env = System.getenv("MCP_1C_STRUCTURE_ZIP_PATH");
        return env != null && !env.isBlank() ? env.trim() : null;
    }

    private static McpJsonMapper getJsonMapper() {
        return ServiceLoader.load(McpJsonMapperSupplier.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier found"))
                .get();
    }

    /** Ленивая загрузка: если задан путь к ZIP и store пуст — загружаем RAG-ZIP. */
    private static String ensureLoaded(InMemoryStore store, String zipPath) {
        if (store.isLoaded()) {
            return null;
        }
        if (zipPath == null || zipPath.isBlank()) {
            return "Данные не загружены. Задайте MCP_1C_STRUCTURE_ZIP_PATH (путь к ZIP-архиву с objects.csv и markdown) или вызовите structure_load_rag_zip.";
        }
        try {
            SnapshotLoader.Snapshot snapshot = RagZipLoader.load(Path.of(zipPath));
            store.load(snapshot);
            return null;
        } catch (Exception e) {
            return "Ошибка загрузки ZIP: " + e.getMessage();
        }
    }

    private static McpSchema.CallToolResult jsonResult(Map<String, ?> data) {
        try {
            String text = JSON.writeValueAsString(data);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(text)))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("{\"error\":\"" + e.getMessage() + "\"}")))
                    .isError(true)
                    .build();
        }
    }

    private static McpSchema.CallToolResult errResult(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    private static void runHttpMode(McpJsonMapper jsonMapper, InMemoryStore store, String zipPath, int port) throws Exception {
        HttpServletStreamableServerTransportProvider httpTransport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        McpSyncServer server = buildServerHttp(store, zipPath, httpTransport);

        Server jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setPort(port);
        jetty.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(httpTransport), MCP_ENDPOINT);
        jetty.setHandler(context);

        jetty.start();
        System.err.println("MCP 1C Structure: HTTP на http://0.0.0.0:" + port + MCP_ENDPOINT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jetty.stop();
            } catch (Exception e) {
                System.err.println("Остановка Jetty: " + e.getMessage());
            }
        }));
        jetty.join();
    }

    private static void runStdioMode(McpJsonMapper jsonMapper, InMemoryStore store, String zipPath) throws InterruptedException {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
        McpSyncServer server = buildServerStdio(store, zipPath, transport);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.closeGracefully();
        }
    }

    private static McpSyncServer buildServerStdio(InMemoryStore store, String zipPath, StdioServerTransportProvider transport) {
        return McpServer.sync(transport).serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_snapshot_info")
                                .title("Информация о снимке")
                                .description("Информация о загруженном снимке структуры конфигурации 1С: имя, версия, дата выгрузки, число объектов.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return jsonResult(Map.of("summary", err));
                            }
                            Meta meta = store.getMeta();
                            if (meta == null || (meta.getConfigName() == null && meta.getSource() == null)) {
                                return jsonResult(Map.of("summary", "Снимок не загружен."));
                            }
                            String summary = String.format("Снимок %s %s, %d объектов, выгрузка от %s.",
                                    meta.getConfigName(), meta.getConfigVersion(), meta.getObjectCount(), meta.getExportedAt());
                            return jsonResult(Map.of(
                                    "summary", summary,
                                    "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                    "configVersion", meta.getConfigVersion() != null ? meta.getConfigVersion() : "",
                                    "exportedAt", meta.getExportedAt() != null ? meta.getExportedAt() : "",
                                    "source", meta.getSource() != null ? meta.getSource() : "",
                                    "objectCount", meta.getObjectCount()
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_search")
                                .title("Поиск объектов")
                                .description("Поиск объектов по имени/синониму (нечёткий поиск). Параметры: query (обязательный), type, limit, offset.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "query", Map.of("type", "string", "description", "Поисковый запрос"),
                                        "type", Map.of("type", "string", "description", "Фильтр по типу метаданных"),
                                        "limit", Map.of("type", "integer", "description", "Макс. количество (по умолчанию 20, макс. 50)"),
                                        "offset", Map.of("type", "integer", "description", "Смещение для постраничной выборки")
                                ), List.of("query"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String query = args.get("query") != null ? args.get("query").toString().trim() : "";
                            if (query.isEmpty()) {
                                return errResult("query обязателен");
                            }
                            String type = args.get("type") != null ? args.get("type").toString() : "";
                            int limit = intArg(args, "limit", 20, 50);
                            int offset = intArg(args, "offset", 0, Integer.MAX_VALUE);

                            InMemoryStore.SearchResult result = store.search(query, type, limit, offset);
                            List<Map<String, String>> matches = new ArrayList<>();
                            for (StructureObject o : result.getMatches()) {
                                matches.add(Map.of(
                                        "id", o.getId() != null ? o.getId() : "",
                                        "type", o.getType() != null ? o.getType() : "",
                                        "name", o.getName() != null ? o.getName() : "",
                                        "synonym", o.getSynonym() != null ? o.getSynonym() : ""
                                ));
                            }
                            return jsonResult(Map.of(
                                    "summary", "Найдено " + result.getTotal() + " объектов.",
                                    "total", result.getTotal(),
                                    "matches", matches
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_get_object")
                                .title("Карточка объекта")
                                .description("Полное описание объекта по идентификатору (objectId).")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "objectId", Map.of("type", "string", "description", "Идентификатор объекта")
                                ), List.of("objectId"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String objectId = args.get("objectId") != null ? args.get("objectId").toString().trim() : "";
                            if (objectId.isEmpty()) {
                                return errResult("objectId обязателен");
                            }
                            StructureObject obj = store.getObject(objectId);
                            if (obj == null) {
                                return errResult("Объект не найден: " + objectId);
                            }
                            return jsonResult(Map.of(
                                    "summary", "Объект " + obj.getName() + ".",
                                    "object", obj,
                                    "source", "rag-zip"
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_list_types")
                                .title("Типы метаданных")
                                .description("Список типов метаданных в снимке и количество объектов по каждому типу.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            List<Map<String, Object>> types = new ArrayList<>();
                            for (InMemoryStore.TypeCount tc : store.listTypes()) {
                                types.add(Map.<String, Object>of("type", tc.getType(), "count", tc.getCount()));
                            }
                            return jsonResult(Map.of(
                                    "summary", "Типы метаданных в снимке.",
                                    "types", types
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_load_rag_zip")
                                .title("Загрузить RAG-ZIP снимок")
                                .description("Загрузить снимок из ZIP в формате mcp-1c-v1: objects.csv (Имя объекта;Тип объекта;Синоним;Файл) и markdown-файлы с описаниями. Всё в памяти, без векторной БД. Параметр: zipPath.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "zipPath", Map.of("type", "string", "description", "Путь к ZIP-архиву выгрузки из 1С (формат ПолучитьТекстСтруктурыКонфигурацииФайлами.epf)")
                                ), List.of("zipPath"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String toLoad = args.get("zipPath") != null ? args.get("zipPath").toString().trim() : "";
                            if (toLoad.isEmpty()) {
                                return errResult("zipPath обязателен — путь к ZIP-архиву с objects.csv и markdown-файлами");
                            }
                            try {
                                SnapshotLoader.Snapshot snapshot = RagZipLoader.load(Path.of(toLoad));
                                store.load(snapshot);
                                Meta meta = snapshot.getMeta();
                                String summary = String.format("RAG-ZIP загружен: объектов %d (описания в памяти, поиск по имени/синониму).",
                                        snapshot.getObjects().size());
                                return jsonResult(Map.of(
                                        "summary", summary,
                                        "objectCount", snapshot.getObjects().size(),
                                        "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                        "source", meta.getSource() != null ? meta.getSource() : "rag-zip"
                                ));
                            } catch (Exception e) {
                                return errResult("Загрузка RAG-ZIP: " + e.getMessage());
                            }
                        }
                )
                .build();
    }

    private static McpSyncServer buildServerHttp(InMemoryStore store, String zipPath, HttpServletStreamableServerTransportProvider httpTransport) {
        return McpServer.sync(httpTransport).serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_snapshot_info")
                                .title("Информация о снимке")
                                .description("Информация о загруженном снимке структуры конфигурации 1С: имя, версия, дата выгрузки, число объектов.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return jsonResult(Map.of("summary", err));
                            }
                            Meta meta = store.getMeta();
                            if (meta == null || (meta.getConfigName() == null && meta.getSource() == null)) {
                                return jsonResult(Map.of("summary", "Снимок не загружен."));
                            }
                            String summary = String.format("Снимок %s %s, %d объектов, выгрузка от %s.",
                                    meta.getConfigName(), meta.getConfigVersion(), meta.getObjectCount(), meta.getExportedAt());
                            return jsonResult(Map.of(
                                    "summary", summary,
                                    "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                    "configVersion", meta.getConfigVersion() != null ? meta.getConfigVersion() : "",
                                    "exportedAt", meta.getExportedAt() != null ? meta.getExportedAt() : "",
                                    "source", meta.getSource() != null ? meta.getSource() : "",
                                    "objectCount", meta.getObjectCount()
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_search")
                                .title("Поиск объектов")
                                .description("Поиск объектов по имени/синониму (нечёткий поиск). Параметры: query (обязательный), type, limit, offset.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "query", Map.of("type", "string", "description", "Поисковый запрос"),
                                        "type", Map.of("type", "string", "description", "Фильтр по типу метаданных"),
                                        "limit", Map.of("type", "integer", "description", "Макс. количество (по умолчанию 20, макс. 50)"),
                                        "offset", Map.of("type", "integer", "description", "Смещение для постраничной выборки")
                                ), List.of("query"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String query = args.get("query") != null ? args.get("query").toString().trim() : "";
                            if (query.isEmpty()) {
                                return errResult("query обязателен");
                            }
                            String type = args.get("type") != null ? args.get("type").toString() : "";
                            int limit = intArg(args, "limit", 20, 50);
                            int offset = intArg(args, "offset", 0, Integer.MAX_VALUE);

                            InMemoryStore.SearchResult result = store.search(query, type, limit, offset);
                            List<Map<String, String>> matches = new ArrayList<>();
                            for (StructureObject o : result.getMatches()) {
                                matches.add(Map.of(
                                        "id", o.getId() != null ? o.getId() : "",
                                        "type", o.getType() != null ? o.getType() : "",
                                        "name", o.getName() != null ? o.getName() : "",
                                        "synonym", o.getSynonym() != null ? o.getSynonym() : ""
                                ));
                            }
                            return jsonResult(Map.of(
                                    "summary", "Найдено " + result.getTotal() + " объектов.",
                                    "total", result.getTotal(),
                                    "matches", matches
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_get_object")
                                .title("Карточка объекта")
                                .description("Полное описание объекта по идентификатору (objectId).")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "objectId", Map.of("type", "string", "description", "Идентификатор объекта")
                                ), List.of("objectId"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String objectId = args.get("objectId") != null ? args.get("objectId").toString().trim() : "";
                            if (objectId.isEmpty()) {
                                return errResult("objectId обязателен");
                            }
                            StructureObject obj = store.getObject(objectId);
                            if (obj == null) {
                                return errResult("Объект не найден: " + objectId);
                            }
                            return jsonResult(Map.of(
                                    "summary", "Объект " + obj.getName() + ".",
                                    "object", obj,
                                    "source", "rag-zip"
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_list_types")
                                .title("Типы метаданных")
                                .description("Список типов метаданных в снимке и количество объектов по каждому типу.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath);
                            if (err != null && !store.isLoaded()) {
                                return errResult(err);
                            }
                            List<Map<String, Object>> types = new ArrayList<>();
                            for (InMemoryStore.TypeCount tc : store.listTypes()) {
                                types.add(Map.<String, Object>of("type", tc.getType(), "count", tc.getCount()));
                            }
                            return jsonResult(Map.of(
                                    "summary", "Типы метаданных в снимке.",
                                    "types", types
                            ));
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_load_rag_zip")
                                .title("Загрузить RAG-ZIP снимок")
                                .description("Загрузить снимок из ZIP в формате mcp-1c-v1: objects.csv (Имя объекта;Тип объекта;Синоним;Файл) и markdown-файлы с описаниями. Всё в памяти, без векторной БД. Параметр: zipPath.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "zipPath", Map.of("type", "string", "description", "Путь к ZIP-архиву выгрузки из 1С (формат ПолучитьТекстСтруктурыКонфигурацииФайлами.epf)")
                                ), List.of("zipPath"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String toLoad = args.get("zipPath") != null ? args.get("zipPath").toString().trim() : "";
                            if (toLoad.isEmpty()) {
                                return errResult("zipPath обязателен — путь к ZIP-архиву с objects.csv и markdown-файлами");
                            }
                            try {
                                SnapshotLoader.Snapshot snapshot = RagZipLoader.load(Path.of(toLoad));
                                store.load(snapshot);
                                Meta meta = snapshot.getMeta();
                                String summary = String.format("RAG-ZIP загружен: объектов %d (описания в памяти, поиск по имени/синониму).",
                                        snapshot.getObjects().size());
                                return jsonResult(Map.of(
                                        "summary", summary,
                                        "objectCount", snapshot.getObjects().size(),
                                        "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                        "source", meta.getSource() != null ? meta.getSource() : "rag-zip"
                                ));
                            } catch (Exception e) {
                                return errResult("Загрузка RAG-ZIP: " + e.getMessage());
                            }
                        }
                )
                .build();
    }

    private static int intArg(Map<String, Object> args, String key, int defaultVal, int maxVal) {
        Object v = args.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number) {
            int n = ((Number) v).intValue();
            return Math.min(Math.max(n, 0), maxVal);
        }
        try {
            int n = Integer.parseInt(v.toString());
            return Math.min(Math.max(n, 0), maxVal);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
