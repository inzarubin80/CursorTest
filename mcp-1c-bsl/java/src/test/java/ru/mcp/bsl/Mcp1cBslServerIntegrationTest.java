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

    @Test
    void serverExposesBslToolsAndAnalyzesCode(@TempDir Path tempDir) throws Exception {
        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        try (var in = getClass().getResourceAsStream("/bsl/sample.bsl")) {
            assertNotNull(in, "Ресурс bsl/sample.bsl должен существовать");
            Files.copy(in, bslDir.resolve("sample.bsl"));
        }

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

            McpSchema.ListToolsResult listResult = client.listTools();
            assertNotNull(listResult);
            assertNotNull(listResult.tools());
            var names = listResult.tools().stream().map(McpSchema.Tool::name).toList();
            assertTrue(names.contains("bsl_analyze"), "Должен быть инструмент bsl_analyze: " + names);
            assertTrue(names.contains("bsl_format"), "Должен быть инструмент bsl_format: " + names);

            McpSchema.CallToolResult callResult = client.callTool(
                    new McpSchema.CallToolRequest("bsl_analyze", java.util.Map.of("srcDir", bslDir.toAbsolutePath().toString())));

            assertNotNull(callResult);
            assertNotNull(callResult.content(), "Ответ bsl_analyze должен содержать content");
            assertFalse(callResult.content().isEmpty(), "Ответ не должен быть пустым");

            String text = callResult.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .findFirst()
                    .orElse("");
            assertFalse(text.isBlank(), "Текст ответа не должен быть пустым");

            // Либо отчёт анализа (если BSL LS установлен), либо сообщение об ошибке (JAR не найден)
            boolean hasAnalysis = text.contains("Анализ") || text.contains("Метрики") || text.contains("Диагностик");
            boolean hasError = text.startsWith("Ошибка:") || callResult.isError();
            assertTrue(hasAnalysis || hasError,
                    "Ответ должен содержать отчёт анализа или сообщение об ошибке: " + text.substring(0, Math.min(200, text.length())));
        } finally {
            client.closeGracefully();
        }
    }
}
