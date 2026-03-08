package ru.mcp.structure;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты MCP-сервера: наличие инструментов, валидация аргументов,
 * эффективность ответов при загруженной фикстуре XML (СтруктураБазыДанных.xml).
 */
class Mcp1cStructureServerIntegrationTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "structure_search",
            "structure_get_object",
            "structure_get_type_usages",
            "structure_list_types",
            "structure_load_structure_xml"
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
    void serverExposesStructureTools() throws Exception {
        withServer(client -> {
            McpSchema.ListToolsResult list = client.listTools();
            assertNotNull(list);
            assertNotNull(list.tools());
            Set<String> names = list.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
            assertEquals(EXPECTED_TOOLS, names, "Ожидаются инструменты: " + names);
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
    void structure_load_structure_xml_noXmlPath_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of()));
            assertTrue(result.isError());
            assertTrue(getTextContent(result).toLowerCase().contains("xmlpath") || getTextContent(result).contains("обязателен"));
        });
    }

    @Test
    void structure_load_structure_xml_nonexistentPath_returnsError() throws Exception {
        withServer(client -> {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", "/nonexistent/structure.xml")));
            assertTrue(result.isError());
            assertTrue(getTextContent(result).contains("Ошибка") || getTextContent(result).contains("Загрузка") || getTextContent(result).contains("Not a file"));
        });
    }

    /** Загружает снимок из СтруктураБазыДанных.xml (если файл есть) и проверяет, что у документа ВзаимодействиеССайтом есть props и tabularSections. */
    @Test
    void structure_load_structure_xml_thenGetObject_returnsPropsAndTabularSections() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            // В CI или без выгрузки XML файла тест пропускаем
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));

            McpSchema.CallToolResult getResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "doc.ВзаимодействиеССайтом")));
            String getText = getTextContent(getResult);
            assertFalse(getResult.isError(), getText);
            assertTrue(getText.contains("ВзаимодействиеССайтом") || getText.contains("Document"), getText);
            assertTrue(getText.contains("props") || getText.contains("tabularSections"),
                    "Ожидаются реквизиты или табличные части в ответе: " + getText.substring(0, Math.min(500, getText.length())));
        });
    }

    /** После загрузки XML константы (НаборКонстант) разворачиваются в объекты типа Constant; поиск с type=Constant возвращает результаты. */
    @Test
    void structure_load_structure_xml_thenSearchConstant_returnsConstants() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));

            McpSchema.CallToolResult searchResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_search", Map.of(
                            "query", "константа",
                            "type", "Constant",
                            "limit", 50)));
            String searchText = getTextContent(searchResult);
            assertFalse(searchResult.isError(), searchText);
            assertTrue(searchText.contains("\"total\":") && !searchText.contains("\"total\":0"),
                    "Ожидается хотя бы один объект типа Constant: " + searchText);

            McpSchema.CallToolResult typesResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_list_types", Map.of()));
            String typesText = getTextContent(typesResult);
            assertTrue(typesText.contains("Constant"), "В structure_list_types должен быть тип Constant: " + typesText);
        });
    }

    /** Вызов MCP: загрузка XML и structure_get_object для doc.ЗаказПациента — вывод результата в stdout. */
    @Test
    void structure_load_xml_thenGetObject_ЗаказПациента_printResult() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            if (loadResult.isError()) {
                System.out.println("Load error: " + getTextContent(loadResult));
                return;
            }
            McpSchema.CallToolResult getResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "doc.ЗаказПациента")));
            System.out.println("--- MCP structure_get_object(doc.ЗаказПациента) ---");
            System.out.println(getTextContent(getResult));
        });
    }

    /** После загрузки XML у объекта-типа (справочник Валюты) в карточке есть поле usedIn. */
    @Test
    void structure_load_xml_thenGetObject_usedInPresent() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));

            McpSchema.CallToolResult getResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "cat.Валюты")));
            String getText = getTextContent(getResult);
            assertFalse(getResult.isError(), getText);
            assertTrue(getText.contains("usedIn"), "В карточке справочника Валюты должно быть поле usedIn: " + getText.substring(0, Math.min(500, getText.length())));
        });
    }

    /** structure_get_type_usages возвращает список использований типа (после загрузки XML). */
    @Test
    void structure_load_xml_thenGetTypeUsages() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));

            McpSchema.CallToolResult usagesResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_type_usages", Map.of("objectId", "cat.Валюты")));
            String usagesText = getTextContent(usagesResult);
            assertFalse(usagesResult.isError(), usagesText);
            assertTrue(usagesText.contains("usedIn") || usagesText.contains("objectId"), "Ответ structure_get_type_usages должен содержать usedIn или objectId: " + usagesText.substring(0, Math.min(400, usagesText.length())));
        });
    }

    // --- Эффективность при загруженных данных (XML) ---
    @Test
    void withLoadedXmlFixture_allToolsReturnEffectiveResults() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }
        withServer(client -> {
            McpSchema.CallToolResult loadResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_load_structure_xml", Map.of("xmlPath", xmlPath.toString())));
            assertFalse(loadResult.isError(), getTextContent(loadResult));
            assertTrue(getTextContent(loadResult).contains("загружен") || getTextContent(loadResult).contains("objectCount"),
                    getTextContent(loadResult));

            McpSchema.CallToolResult searchResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_search", Map.of("query", "Валюты")));
            String searchText = getTextContent(searchResult);
            assertFalse(searchResult.isError(), searchText);
            assertTrue(searchText.contains("total") && (searchText.contains("matches") || searchText.contains("1")),
                    searchText);

            McpSchema.CallToolResult getResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_get_object", Map.of("objectId", "cat.Валюты")));
            String getText = getTextContent(getResult);
            assertFalse(getResult.isError(), getText);
            assertTrue(getText.contains("Валюты") && (getText.contains("Catalog") || getText.contains("Справочник")),
                    getText);
            assertTrue(getText.contains("props") || getText.contains("tabularSections") || getText.contains("id"),
                    "Ожидаются props/tabularSections или id: " + getText.substring(0, Math.min(300, getText.length())));

            McpSchema.CallToolResult typesResult = client.callTool(
                    new McpSchema.CallToolRequest("structure_list_types", Map.of()));
            String typesText = getTextContent(typesResult);
            assertTrue(typesText.contains("Catalog") || typesText.contains("types") || typesText.contains("type"),
                    typesText);
        });
    }
}
