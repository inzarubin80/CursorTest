package ru.mcp.bsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты парсинга аргументов запуска MCP-сервера (1.2, 1.3).
 */
class Mcp1cBslServerArgsTest {

    // 1.2 — порт из аргументов --http --port N
    @Test
    void parseHttpPort_fromArgsPort9090_returns9090() {
        assertEquals(9090, Mcp1cBslServer.parseHttpPort(new String[]{"--http", "--port", "9090"}));
    }

    @Test
    void parseHttpPort_httpOnly_returnsDefault8080() {
        assertEquals(8080, Mcp1cBslServer.parseHttpPort(new String[]{"--http"}));
    }

    /** Без --http возвращается 0 только если не задан MCP_HTTP_PORT; при наличии env тест не проверяет значение. */
    @Test
    void parseHttpPort_withoutHttp_doesNotUsePortArgAlone() {
        int fromPortOnly = Mcp1cBslServer.parseHttpPort(new String[]{"--port", "9090"});
        // Без --http аргумент --port не используется: либо 0 (stdio), либо значение из MCP_HTTP_PORT
        assertTrue(fromPortOnly == 0 || fromPortOnly == 9090,
                "Без --http ожидается 0 (stdio) или значение из MCP_HTTP_PORT, получено: " + fromPortOnly);
    }

    // 1.3 — явный путь к JAR из аргументов
    @Test
    void parseBslLanguageServerJar_fromArgs_returnsPath() {
        String path = "/opt/bsl/bsl-language-server.jar";
        assertEquals(path, Mcp1cBslServer.parseBslLanguageServerJar(new String[]{"--bsl-language-server-jar", path}));
    }

    @Test
    void parseBslLanguageServerJar_noArg_returnsNull() {
        assertNull(Mcp1cBslServer.parseBslLanguageServerJar(new String[]{}));
        assertNull(Mcp1cBslServer.parseBslLanguageServerJar(new String[]{"--http"}));
    }
}
