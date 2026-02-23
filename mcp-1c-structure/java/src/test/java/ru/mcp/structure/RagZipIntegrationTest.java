package ru.mcp.structure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import ru.mcp.structure.snapshot.RagZipLoader;
import ru.mcp.structure.store.InMemoryStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест: загрузка RAG-ZIP (objects.csv + markdown), поиск, get_object с content.
 * Использует ZIP из корня проекта mcp-1c-structure: ОписаниеКонфигурации_Тест.zip
 */
class RagZipIntegrationTest {

    private InMemoryStore store;
    private static final Path ZIP_PATH = findRagZip();

    private static Path findRagZip() {
        String zipName = "ОписаниеКонфигурации_Тест.zip";
        Path cwd = Paths.get(System.getProperty("user.dir", "."));
        Path[] candidates = {
                cwd.resolve(zipName),
                cwd.getParent().resolve(zipName),
                cwd.resolve("mcp-1c-structure").resolve(zipName),
                Paths.get("..").resolve(zipName).toAbsolutePath().normalize(),
                Paths.get("..").resolve("mcp-1c-structure").resolve(zipName).toAbsolutePath().normalize()
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static boolean zipExists() {
        return ZIP_PATH != null && Files.isRegularFile(ZIP_PATH);
    }

    @BeforeEach
    void setUp() throws Exception {
        store = new InMemoryStore();
        if (zipExists()) {
            store.load(RagZipLoader.load(ZIP_PATH));
        }
    }

    @Test
    @EnabledIf("zipExists")
    void loadRagZipAndMeta() {
        assertTrue(store.isLoaded());
        assertNotNull(store.getMeta());
        assertEquals("rag-zip", store.getMeta().getSource());
        assertTrue(store.getMeta().getObjectCount() > 0);
    }

    @Test
    @EnabledIf("zipExists")
    void searchByObjectName() {
        InMemoryStore.SearchResult result = store.search("AETitles", null, 20, 0);
        assertTrue(result.getTotal() >= 1);
        assertTrue(result.getMatches().stream()
                .anyMatch(o -> "AETitles".equals(o.getName()) || (o.getName() != null && o.getName().contains("AETitles"))));
    }

    @Test
    @EnabledIf("zipExists")
    void getObjectHasContent() {
        var obj = store.getObject("cat.AETitles");
        if (obj != null) {
            assertNotNull(obj.getContent());
            assertFalse(obj.getContent().isBlank());
        }
    }

    @Test
    @EnabledIf("zipExists")
    void searchByContentSubstring() {
        // поиск по тексту из описания (content)
        InMemoryStore.SearchResult result = store.search("Application Entities", null, 20, 0);
        assertTrue(result.getTotal() >= 1);
    }

    @Test
    @EnabledIf("zipExists")
    void listTypes() {
        var types = store.listTypes();
        assertFalse(types.isEmpty());
    }

    @Test
    void zipNotFoundWhenDisabled() {
        // Когда ZIP нет — тесты с @EnabledIf("zipExists") пропускаются, store не загружен
        if (!zipExists()) {
            assertNull(ZIP_PATH);
            assertFalse(store.isLoaded());
        }
    }
}

