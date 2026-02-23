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
import ru.mcp.structure.snapshot.StructureXmlLoader;
import ru.mcp.structure.snapshot.StructureObject;
import ru.mcp.structure.store.InMemoryStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            String xmlPath = parseXmlPath(args);
            McpJsonMapper jsonMapper = getJsonMapper();
            InMemoryStore store = new InMemoryStore();

            if (httpPort > 0) {
                runHttpMode(jsonMapper, store, zipPath, xmlPath, httpPort);
            } else {
                runStdioMode(jsonMapper, store, zipPath, xmlPath);
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

    private static String parseXmlPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--xml-path".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        String env = System.getenv("MCP_1C_STRUCTURE_XML_PATH");
        return env != null && !env.isBlank() ? env.trim() : null;
    }

    private static McpJsonMapper getJsonMapper() {
        return ServiceLoader.load(McpJsonMapperSupplier.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier found"))
                .get();
    }

    /** Ленивая загрузка: если задан путь к ZIP или XML и store пуст — загружаем (ZIP приоритетнее). */
    private static String ensureLoaded(InMemoryStore store, String zipPath, String xmlPath) {
        if (store.isLoaded()) {
            return null;
        }
        if (zipPath != null && !zipPath.isBlank()) {
            try {
                SnapshotLoader.Snapshot snapshot = RagZipLoader.load(Path.of(zipPath));
                store.load(snapshot);
                return null;
            } catch (Exception e) {
                return "Ошибка загрузки ZIP: " + e.getMessage();
            }
        }
        if (xmlPath != null && !xmlPath.isBlank()) {
            try {
                SnapshotLoader.Snapshot snapshot = StructureXmlLoader.load(Path.of(xmlPath));
                store.load(snapshot);
                return null;
            } catch (Exception e) {
                return "Ошибка загрузки XML: " + e.getMessage();
            }
        }
        return "Данные не загружены. Задайте MCP_1C_STRUCTURE_ZIP_PATH или MCP_1C_STRUCTURE_XML_PATH (или вызовите structure_load_rag_zip / structure_load_structure_xml).";
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

    private static void runHttpMode(McpJsonMapper jsonMapper, InMemoryStore store, String zipPath, String xmlPath, int port) throws Exception {
        HttpServletStreamableServerTransportProvider httpTransport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        McpSyncServer server = buildServerHttp(store, zipPath, xmlPath, httpTransport);

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

    private static void runStdioMode(McpJsonMapper jsonMapper, InMemoryStore store, String zipPath, String xmlPath) throws InterruptedException {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
        McpSyncServer server = buildServerStdio(store, zipPath, xmlPath, transport);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.closeGracefully();
        }
    }

    private static McpSyncServer buildServerStdio(InMemoryStore store, String zipPath, String xmlPath, StdioServerTransportProvider transport) {
        return McpServer.sync(transport).serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                                Map<String, String> m = new LinkedHashMap<>();
                                m.put("id", o.getId() != null ? o.getId() : "");
                                m.put("type", o.getType() != null ? o.getType() : "");
                                m.put("name", o.getName() != null ? o.getName() : "");
                                m.put("synonym", o.getSynonym() != null ? o.getSynonym() : "");
                                if ("Constant".equals(o.getType()) && o.getValueType() != null && !o.getValueType().isEmpty()) {
                                    m.put("valueType", o.getValueType());
                                }
                                matches.add(m);
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                                .name("structure_get_type_usages")
                                .title("Где используется тип")
                                .description("Список использований объекта как типа (реквизиты, ТЧ, константы). Параметр: objectId (например cat.Номенклатура). Заполняется только при загрузке из XML.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "objectId", Map.of("type", "string", "description", "Идентификатор объекта-типа (например cat.Валюты)")
                                ), List.of("objectId"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                            List<ru.mcp.structure.snapshot.TypeUsage> usedIn = obj.getUsedIn();
                            if (usedIn == null) {
                                usedIn = List.of();
                            }
                            return jsonResult(Map.of(
                                    "summary", "Использований типа «" + obj.getName() + "»: " + usedIn.size() + ".",
                                    "objectId", objectId,
                                    "objectName", obj.getName() != null ? obj.getName() : "",
                                    "usedIn", usedIn
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_load_structure_xml")
                                .title("Загрузить снимок из СтруктураБазыДанных.xml")
                                .description("Загрузить снимок структуры из XML «Структура базы данных» (реквизиты и табличные части). Параметр: xmlPath.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "xmlPath", Map.of("type", "string", "description", "Путь к файлу СтруктураБазыДанных.xml")
                                ), List.of("xmlPath"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String pathToXml = args.get("xmlPath") != null ? args.get("xmlPath").toString().trim() : "";
                            if (pathToXml.isEmpty()) {
                                return errResult("xmlPath обязателен — путь к файлу СтруктураБазыДанных.xml");
                            }
                            try {
                                SnapshotLoader.Snapshot snapshot = StructureXmlLoader.load(Path.of(pathToXml));
                                store.load(snapshot);
                                Meta meta = snapshot.getMeta();
                                String summary = String.format("Снимок из XML загружен: объектов %d (реквизиты и табличные части).",
                                        snapshot.getObjects().size());
                                return jsonResult(Map.of(
                                        "summary", summary,
                                        "objectCount", snapshot.getObjects().size(),
                                        "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                        "source", meta.getSource() != null ? meta.getSource() : "structure-xml"
                                ));
                            } catch (Exception e) {
                                String msg = e.getMessage();
                                if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
                                if (msg == null) msg = e.getClass().getSimpleName();
                                return errResult("Загрузка XML: " + msg);
                            }
                        }
                )
                .build();
    }

    private static McpSyncServer buildServerHttp(InMemoryStore store, String zipPath, String xmlPath, HttpServletStreamableServerTransportProvider httpTransport) {
        return McpServer.sync(httpTransport).serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                                Map<String, String> m = new LinkedHashMap<>();
                                m.put("id", o.getId() != null ? o.getId() : "");
                                m.put("type", o.getType() != null ? o.getType() : "");
                                m.put("name", o.getName() != null ? o.getName() : "");
                                m.put("synonym", o.getSynonym() != null ? o.getSynonym() : "");
                                if ("Constant".equals(o.getType()) && o.getValueType() != null && !o.getValueType().isEmpty()) {
                                    m.put("valueType", o.getValueType());
                                }
                                matches.add(m);
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                                .name("structure_get_type_usages")
                                .title("Где используется тип")
                                .description("Список использований объекта как типа (реквизиты, ТЧ, константы). Параметр: objectId (например cat.Номенклатура). Заполняется только при загрузке из XML.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "objectId", Map.of("type", "string", "description", "Идентификатор объекта-типа (например cat.Валюты)")
                                ), List.of("objectId"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                            List<ru.mcp.structure.snapshot.TypeUsage> usedIn = obj.getUsedIn();
                            if (usedIn == null) {
                                usedIn = List.of();
                            }
                            return jsonResult(Map.of(
                                    "summary", "Использований типа «" + obj.getName() + "»: " + usedIn.size() + ".",
                                    "objectId", objectId,
                                    "objectName", obj.getName() != null ? obj.getName() : "",
                                    "usedIn", usedIn
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
                            String err = ensureLoaded(store, zipPath, xmlPath);
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
                .tool(
                        McpSchema.Tool.builder()
                                .name("structure_load_structure_xml")
                                .title("Загрузить снимок из СтруктураБазыДанных.xml")
                                .description("Загрузить снимок структуры из XML «Структура базы данных» (реквизиты и табличные части). Параметр: xmlPath.")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                                        "xmlPath", Map.of("type", "string", "description", "Путь к файлу СтруктураБазыДанных.xml")
                                ), List.of("xmlPath"), null, null, null))
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            String pathToXml = args.get("xmlPath") != null ? args.get("xmlPath").toString().trim() : "";
                            if (pathToXml.isEmpty()) {
                                return errResult("xmlPath обязателен — путь к файлу СтруктураБазыДанных.xml");
                            }
                            try {
                                SnapshotLoader.Snapshot snapshot = StructureXmlLoader.load(Path.of(pathToXml));
                                store.load(snapshot);
                                Meta meta = snapshot.getMeta();
                                String summary = String.format("Снимок из XML загружен: объектов %d (реквизиты и табличные части).",
                                        snapshot.getObjects().size());
                                return jsonResult(Map.of(
                                        "summary", summary,
                                        "objectCount", snapshot.getObjects().size(),
                                        "configName", meta.getConfigName() != null ? meta.getConfigName() : "",
                                        "source", meta.getSource() != null ? meta.getSource() : "structure-xml"
                                ));
                            } catch (Exception e) {
                                String msg = e.getMessage();
                                if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
                                if (msg == null) msg = e.getClass().getSimpleName();
                                return errResult("Загрузка XML: " + msg);
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
