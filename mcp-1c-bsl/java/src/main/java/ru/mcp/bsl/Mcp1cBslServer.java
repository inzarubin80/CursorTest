package ru.mcp.bsl;

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

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * MCP-сервер для 1С (BSL/OneScript): анализ и форматирование через BSL Language Server.
 * Режимы: stdio (по умолчанию) или HTTP (--http [--port 8080] или переменная MCP_HTTP_PORT).
 */
public final class Mcp1cBslServer {

    private static final String NAME = "mcp-1c-bsl";
    private static final String VERSION = "0.1.0";
    private static final String MCP_ENDPOINT = "/mcp";
    private static final int DEFAULT_HTTP_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int httpPort = parseHttpPort(args);
        McpJsonMapper jsonMapper = getJsonMapper();
        BslRunner bsl = new BslRunner();

        if (httpPort > 0) {
            runHttpMode(jsonMapper, bsl, httpPort);
        } else {
            runStdioMode(jsonMapper, bsl);
        }
    }

    /** Порт из MCP_HTTP_PORT или из аргументов --http --port N. 0 = stdio. */
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

    private static McpJsonMapper getJsonMapper() {
        return ServiceLoader.load(McpJsonMapperSupplier.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier found (add mcp-json-jackson2 or mcp-json-jackson3 to classpath)"))
                .get();
    }

    /** Схема аргументов для bsl_analyze (srcDir). */
    private static McpSchema.JsonSchema bslAnalyzeInputSchema() {
        return new McpSchema.JsonSchema(
                "object",
                Map.of("srcDir", Map.of(
                        "type", "string",
                        "description", "Путь к каталогу с исходниками или к файлу .bsl/.os")),
                List.of("srcDir"),
                null, null, null);
    }

    /** Схема аргументов для bsl_format (src). */
    private static McpSchema.JsonSchema bslFormatInputSchema() {
        return new McpSchema.JsonSchema(
                "object",
                Map.of("src", Map.of(
                        "type", "string",
                        "description", "Путь к файлу или каталогу для форматирования")),
                List.of("src"),
                null, null, null);
    }

    private static void runHttpMode(McpJsonMapper jsonMapper, BslRunner bsl, int port) throws Exception {
        HttpServletStreamableServerTransportProvider httpTransport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        McpSyncServer server = buildServerWithHttp(bsl, httpTransport);

        Server jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setPort(port);
        jetty.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(httpTransport), MCP_ENDPOINT);
        jetty.setHandler(context);

        jetty.start();
        System.err.println("MCP 1C BSL: HTTP на http://0.0.0.0:" + port + MCP_ENDPOINT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jetty.stop();
            } catch (Exception e) {
                System.err.println("Остановка Jetty: " + e.getMessage());
            }
        }));
        jetty.join();
    }

    private static void runStdioMode(McpJsonMapper jsonMapper, BslRunner bsl) throws InterruptedException {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
        McpSyncServer server = buildServerWithStdio(bsl, transport);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.closeGracefully();
        }
    }

    private static McpSyncServer buildServerWithStdio(BslRunner bsl, StdioServerTransportProvider transport) {
        return McpServer.sync(transport)
                .serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tool(
                        McpSchema.Tool.builder()
                                .name("bsl_analyze")
                                .title("Анализ кода 1С (BSL)")
                                .description("Запускает анализ кода 1С (BSL/OneScript) через BSL Language Server. " +
                                        "Возвращает диагностики и метрики. Требуется Java 17+ и JAR BSL LS (BSL_LANGUAGE_SERVER_JAR).")
                                .inputSchema(bslAnalyzeInputSchema())
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            Object srcDir = args.get("srcDir");
                            String path = srcDir != null ? srcDir.toString().trim() : "";
                            if (path.isEmpty()) {
                                return McpSchema.CallToolResult.builder()
                                        .content(List.of(new McpSchema.TextContent("Укажите srcDir — путь к каталогу или файлу .bsl/.os")))
                                        .isError(true)
                                        .build();
                            }
                            String result = bsl.analyze(path);
                            boolean isError = result.startsWith("Ошибка:");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(result)))
                                    .isError(isError)
                                    .build();
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("bsl_format")
                                .title("Форматирование кода 1С (BSL)")
                                .description("Форматирует файлы 1С (BSL/OneScript) через BSL Language Server. " +
                                        "Путь — к файлу .bsl/.os или каталогу. Требуется Java 17+ и JAR (BSL_LANGUAGE_SERVER_JAR).")
                                .inputSchema(bslFormatInputSchema())
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            Object src = args.get("src");
                            String path = src != null ? src.toString().trim() : "";
                            if (path.isEmpty()) {
                                return McpSchema.CallToolResult.builder()
                                        .content(List.of(new McpSchema.TextContent("Укажите src — путь к файлу или каталогу")))
                                        .isError(true)
                                        .build();
                            }
                            String result = bsl.format(path);
                            boolean isError = result.startsWith("Ошибка:");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(result)))
                                    .isError(isError)
                                    .build();
                        }
                )
                .build();
    }

    private static McpSyncServer buildServerWithHttp(BslRunner bsl, HttpServletStreamableServerTransportProvider httpTransport) {
        return McpServer.sync(httpTransport)
                .serverInfo(NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tool(
                        McpSchema.Tool.builder()
                                .name("bsl_analyze")
                                .title("Анализ кода 1С (BSL)")
                                .description("Запускает анализ кода 1С (BSL/OneScript) через BSL Language Server. " +
                                        "Возвращает диагностики и метрики. Требуется Java 17+ и JAR BSL LS (BSL_LANGUAGE_SERVER_JAR).")
                                .inputSchema(bslAnalyzeInputSchema())
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            Object srcDir = args.get("srcDir");
                            String path = srcDir != null ? srcDir.toString().trim() : "";
                            if (path.isEmpty()) {
                                return McpSchema.CallToolResult.builder()
                                        .content(List.of(new McpSchema.TextContent("Укажите srcDir — путь к каталогу или файлу .bsl/.os")))
                                        .isError(true)
                                        .build();
                            }
                            String result = bsl.analyze(path);
                            boolean isError = result.startsWith("Ошибка:");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(result)))
                                    .isError(isError)
                                    .build();
                        }
                )
                .tool(
                        McpSchema.Tool.builder()
                                .name("bsl_format")
                                .title("Форматирование кода 1С (BSL)")
                                .description("Форматирует файлы 1С (BSL/OneScript) через BSL Language Server. " +
                                        "Путь — к файлу .bsl/.os или каталогу. Требуется Java 17+ и JAR (BSL_LANGUAGE_SERVER_JAR).")
                                .inputSchema(bslFormatInputSchema())
                                .build(),
                        (exchange, arguments) -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
                            Object src = args.get("src");
                            String path = src != null ? src.toString().trim() : "";
                            if (path.isEmpty()) {
                                return McpSchema.CallToolResult.builder()
                                        .content(List.of(new McpSchema.TextContent("Укажите src — путь к файлу или каталогу")))
                                        .isError(true)
                                        .build();
                            }
                            String result = bsl.format(path);
                            boolean isError = result.startsWith("Ошибка:");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(result)))
                                    .isError(isError)
                                    .build();
                        }
                )
                .build();
    }
}
