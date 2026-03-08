package ru.mcp.structure;

import org.junit.jupiter.api.Test;
import ru.mcp.structure.snapshot.SnapshotLoader;
import ru.mcp.structure.snapshot.StructureXmlLoader;
import ru.mcp.structure.store.InMemoryStore;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Замер времени загрузки СтруктураБазыДанных.xml (парсинг XML + загрузка в store с TF-IDF).
 */
class StructureXmlLoadBenchmarkTest {

    @Test
    void loadXmlAndStore_timing() throws Exception {
        Path xmlPath = Path.of("..", "СтруктураБазыДанных.xml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(xmlPath)) {
            return;
        }

        long t0 = System.nanoTime();
        SnapshotLoader.Snapshot snapshot = StructureXmlLoader.load(xmlPath);
        long t1 = System.nanoTime();
        InMemoryStore store = new InMemoryStore();
        store.load(snapshot);
        long t2 = System.nanoTime();

        long xmlMs = (t1 - t0) / 1_000_000;
        long storeMs = (t2 - t1) / 1_000_000;
        long totalMs = (t2 - t0) / 1_000_000;

        System.out.println("СтруктураБазыДанных.xml загрузка:");
        System.out.println("  Парсинг XML (StAX): " + xmlMs + " мс");
        System.out.println("  Store + TF-IDF:     " + storeMs + " мс");
        System.out.println("  Всего:              " + totalMs + " мс");
        System.out.println("  Объектов:           " + snapshot.getObjects().size());

        assertTrue(store.isLoaded());
    }
}
