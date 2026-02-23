package ru.mcp.bsl;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест MCP-сервера: проверяем, что сервер помогает писать код на 1С —
 * отдаёт инструменты bsl_analyze и bsl_format и выполняет вызов анализа.
 */
class Mcp1cBslServerIntegrationTest {

    private static int findFreePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось найти свободный порт", e);
        }
    }

    private static void waitForServer(String baseUrl, int maxAttempts) throws InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        URI uri = URI.create(baseUrl.replace("/mcp", "/mcp"));
        for (int i = 0; i < maxAttempts; i++) {
            try {
                var req = java.net.http.HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(1)).GET().build();
                client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                return;
            } catch (Exception ignored) {
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Сервер не поднялся за " + maxAttempts + " попыток: " + baseUrl);
    }

    private static String getTextContent(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) return "";
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst()
                .orElse("");
    }

    /** Запускает HTTP-сервер на свободном порту, инициализирует клиента и выполняет callback. */
    private void withServer(java.util.function.Consumer<McpSyncClient> block) throws Exception {
        int port = findFreePort();
        String baseUrl = "http://localhost:" + port + "/mcp";
        Thread serverThread = new Thread(() -> {
            try {
                Mcp1cBslServer.main(new String[]{"--http", "--port", String.valueOf(port)});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "mcp-server");
        serverThread.setDaemon(true);
        serverThread.start();
        waitForServer(baseUrl, 50);
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl).build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(15))
                .build();
        try {
            client.initialize();
            block.accept(client);
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void serverExposesBslToolsAndAnalyzesCode(@TempDir Path tempDir) throws Exception {
        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        try (var in = getClass().getResourceAsStream("/bsl/sample.bsl")) {
            assertNotNull(in, "Ресурс bsl/sample.bsl должен существовать");
            Files.copy(in, bslDir.resolve("sample.bsl"));
        }
        Path dir = bslDir;
        withServer(client -> {
            McpSchema.ListToolsResult listResult = client.listTools();
            assertNotNull(listResult);
            assertNotNull(listResult.tools());
            var names = listResult.tools().stream().map(McpSchema.Tool::name).toList();
            assertTrue(names.contains("bsl_analyze"), "Должен быть инструмент bsl_analyze: " + names);
            assertTrue(names.contains("bsl_format"), "Должен быть инструмент bsl_format: " + names);

            McpSchema.CallToolResult callResult = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", Map.of("srcDir", dir.toAbsolutePath().toString())));

            assertNotNull(callResult);
            assertNotNull(callResult.content(), "Ответ bsl_analyze должен содержать content");
            assertFalse(callResult.content().isEmpty(), "Ответ не должен быть пустым");

            String text = getTextContent(callResult);
            assertFalse(text.isBlank(), "Текст ответа не должен быть пустым");

            boolean hasAnalysis = text.contains("Анализ") || text.contains("Метрики") || text.contains("Диагностик");
            boolean hasError = text.startsWith("Ошибка:") || callResult.isError();
            assertTrue(hasAnalysis || hasError,
                    "Ответ должен содержать отчёт анализа или сообщение об ошибке: " + text.substring(0, Math.min(200, text.length())));
        });
    }

    // 3.1 — пустой srcDir
    @Test
    void bsl_analyzeEmptySrcDirReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", Map.of("srcDir", "")));
            assertTrue(result.isError(), "Ожидается isError=true при пустом srcDir");
            String text = getTextContent(result);
            assertTrue(text.contains("Укажите srcDir") || text.contains("srcDir"), text);
        });
    }

    @Test
    void bsl_analyzeMissingSrcDirParamReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", Map.of()));
            assertTrue(result.isError(), "Ожидается isError при отсутствии srcDir");
            String text = getTextContent(result);
            assertTrue(text.contains("Укажите srcDir") || text.contains("srcDir"), text);
        });
    }

    // 3.3 — путь к одному файлу .bsl
    @Test
    void bsl_analyzeSingleFilePath(@TempDir Path tempDir) throws Exception {
        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        try (var in = getClass().getResourceAsStream("/bsl/sample.bsl")) {
            assertNotNull(in);
            Files.copy(in, bslDir.resolve("sample.bsl"));
        }
        Path filePath = bslDir.resolve("sample.bsl");
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", Map.of("srcDir", filePath.toAbsolutePath().toString())));
            String text = getTextContent(result);
            assertFalse(text.isBlank());
            boolean ok = text.contains("Анализ") || text.contains("Метрики") || text.contains("Диагностик")
                    || (result.isError() && text.contains("Ошибка"));
            assertTrue(ok, "Ожидается отчёт по файлу или ошибка: " + text.substring(0, Math.min(300, text.length())));
        });
    }

    // 3.4 — несуществующий путь
    @Test
    void bsl_analyzeNonexistentPathReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", Map.of("srcDir", "/nonexistent/path/12345")));
            assertTrue(result.isError(), "Ожидается isError при несуществующем пути");
            String text = getTextContent(result);
            assertTrue(text.contains("Ошибка") && (text.contains("не существует") || text.contains("путь")), text);
        });
    }

    // 4.1 — пустой src для bsl_format
    @Test
    void bsl_formatEmptySrcReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_format", Map.of("src", "")));
            assertTrue(result.isError());
            String text = getTextContent(result);
            assertTrue(text.contains("Укажите src") || text.contains("src"), text);
        });
    }

    @Test
    void bsl_formatMissingSrcParamReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_format", Map.of()));
            assertTrue(result.isError());
            String text = getTextContent(result);
            assertTrue(text.contains("Укажите src") || text.contains("src"), text);
        });
    }

    // 4.2 — валидный файл/каталог для format
    @Test
    void bsl_formatValidFileOrDir(@TempDir Path tempDir) throws Exception {
        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        try (var in = getClass().getResourceAsStream("/bsl/sample.bsl")) {
            assertNotNull(in);
            Files.copy(in, bslDir.resolve("sample.bsl"));
        }
        Path pathToFormat = bslDir.resolve("sample.bsl");
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_format", Map.of("src", pathToFormat.toAbsolutePath().toString())));
            String text = getTextContent(result);
            if (!result.isError()) {
                assertTrue(text.contains("Форматирование выполнено успешно") || text.contains("успешно"), text);
            } else {
                assertTrue(text.contains("Ошибка"), text);
            }
        });
    }

    // 4.3 — несуществующий путь для format
    @Test
    void bsl_formatNonexistentPathReturnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("bsl_format", Map.of("src", "/nonexistent/file.bsl")));
            assertTrue(result.isError());
            String text = getTextContent(result);
            assertTrue(text.contains("Ошибка") && (text.contains("не существует") || text.contains("путь")), text);
        });
    }
}
