package ru.mcp.structure;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты MCP-сервера: наличие инструментов, валидация аргументов,
 * эффективность ответов при загруженной фикстуре RAG-ZIP.
 */
class Mcp1cStructureServerIntegrationTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "structure_search",
            "structure_get_object",
            "structure_list_types",
            "structure_load_rag_zip"
    );

    private static int findFreePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось найти свободный порт", e);
        }
    }

    private static void waitForServer(String baseUrl, int maxAttempts) throws InterruptedException {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        URI uri = URI.create(baseUrl);
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

    /** Собирает минимальный RAG-ZIP из ресурсов в tempDir и возвращает путь к архиву. */
    private static Path buildFixtureZip(Path tempDir) throws Exception {
        Path zipPath = tempDir.resolve("mini-structure.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            byte[] csv = ("Имя объекта;Тип объекта;Синоним;Файл\n" +
                    "AETitles;Справочник;Application Entity Titles;doc/AETitles.md\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("objects.csv"));
            zos.write(csv);
            zos.closeEntry();

            byte[] md = "# Справочник AETitles\n\nКраткое описание для тестов MCP. Application Entity Titles.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("doc/AETitles.md"));
            zos.write(md);
            zos.closeEntry();
        }
        return zipPath;
    }

    private void withServer(java.util.function.Consumer<McpSyncClient> block) throws Exception {
        int port = findFreePort();
        String baseUrl = "http://localhost:" + port + "/mcp";
        Thread serverThread = new Thread(() -> {
            try {
                Mcp1cStructureServer.main(new String[]{"--http", "--port", String.valueOf(port)});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "mcp-structure-server");
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

    // --- Уровень протокола: наличие инструментов ---
    @Test
    void serverExposesFourStructureTools() throws Exception {
        withServer(client -> {
            McpSchema.ListToolsResult list = client.listTools();
            assertNotNull(list);
            assertNotNull(list.tools());
            Set<String> names = list.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
            assertEquals(EXPECTED_TOOLS, names, "Ожидаются ровно 4 инструмента: " + names);
        });
    }

    // --- Валидация аргументов ---
    @Test
    void structure_search_emptyQuery_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_search", Map.of("query", "")));
            assertTrue(result.isError() || getTextContent(result).contains("query") || getTextContent(result).contains("обязателен"),
                    getTextContent(result));
        });
    }

    @Test
    void structure_get_object_withoutLoad_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "cat.AETitles")));
            String text = getTextContent(result);
            assertTrue(result.isError() || text.contains("не загружен") || text.contains("не найден") || text.contains("Объект"),
                    "Ожидается isError или сообщение об ошибке: " + text);
        });
    }

    @Test
    void structure_get_object_emptyObjectId_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "")));
            String text = getTextContent(result);
            assertTrue(result.isError() || text.toLowerCase().contains("objectid") || text.contains("обязателен"),
                    "Ожидается isError или сообщение про objectId: " + text);
        });
    }

    @Test
    void structure_load_rag_zip_noZipPath_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_rag_zip", Map.of()));
            assertTrue(result.isError());
            assertTrue(getTextContent(result).toLowerCase().contains("zippath") || getTextContent(result).contains("обязателен"));
        });
    }

    @Test
    void structure_load_rag_zip_nonexistentPath_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_rag_zip", Map.of("zipPath", "/nonexistent/rag.zip")));
            assertTrue(result.isError());
            assertTrue(getTextContent(result).contains("Ошибка") || getTextContent(result).contains("Загрузка"));
        });
    }

    // --- Эффективность при загруженных данных ---
    @Test
    void withLoadedFixture_allToolsReturnEffectiveResults(@TempDir Path tempDir) throws Exception {
        Path fixtureZip = buildFixtureZip(tempDir);
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_rag_zip", Map.of("zipPath", fixtureZip.toAbsolutePath().toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));
            assertTrue(getTextContent(loadResult).contains("загружен") || getTextContent(loadResult).contains("objectCount"),
                    getTextContent(loadResult));

            McpSchema.CallToolResult searchResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_search", Map.of("query", "AETitles")));
            String searchText = getTextContent(searchResult);
            assertFalse(searchResult.isError(), searchText);
            assertTrue(searchText.contains("total") && (searchText.contains("1") || searchText.contains("matches")),
                    searchText);
            assertTrue(searchText.contains("AETitles") || searchText.contains("cat."), searchText);

            McpSchema.CallToolResult getResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "cat.AETitles")));
            String getText = getTextContent(getResult);
            assertFalse(getResult.isError(), getText);
            assertTrue(getText.contains("AETitles") && (getText.contains("Catalog") || getText.contains("Справочник")),
                    getText);
            assertTrue(getText.contains("content") || getText.contains("Application Entity") || getText.contains("описание"),
                    "Ожидается content или описание: " + getText.substring(0, Math.min(300, getText.length())));

            McpSchema.CallToolResult typesResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_list_types", Map.of()));
            String typesText = getTextContent(typesResult);
            assertTrue(typesText.contains("Catalog") || typesText.contains("types") || typesText.contains("type"),
                    typesText);
        });
    }
}
