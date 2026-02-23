package ru.mcp.structure.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Загрузка снимка структуры 1С из одного JSON-файла с полями meta, objects, relations.
 */
public final class SnapshotLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Загружает снимок из одного JSON-файла с полями meta, objects, relations.
     *
     * @param path путь к JSON-файлу
     * @return снимок (meta, objects, relations)
     */
    public static Snapshot load(Path path) throws IOException {
        Path p = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) {
            throw new IOException("Not a file: " + p);
        }
        byte[] bytes = Files.readAllBytes(p);
        SnapshotFile wrapper = MAPPER.readValue(bytes, SnapshotFile.class);
        if (wrapper.meta == null) {
            throw new IOException("Missing 'meta' in snapshot file");
        }
        List<StructureObject> objects = wrapper.objects != null ? wrapper.objects : Collections.emptyList();
        List<Relation> relations = wrapper.relations != null ? wrapper.relations : Collections.emptyList();
        return new Snapshot(wrapper.meta, objects, relations);
    }

    /** Обёртка для JSON с полями meta, objects, relations. */
    @SuppressWarnings("unused")
    private static final class SnapshotFile {
        public Meta meta;
        public List<StructureObject> objects;
        public List<Relation> relations;
    }

    /** Результат загрузки снимка. */
    public static final class Snapshot {
        private final Meta meta;
        private final List<StructureObject> objects;
        private final List<Relation> relations;

        public Snapshot(Meta meta, List<StructureObject> objects, List<Relation> relations) {
            this.meta = meta;
            this.objects = objects != null ? List.copyOf(objects) : List.of();
            this.relations = relations != null ? List.copyOf(relations) : List.of();
        }

        public Meta getMeta() {
            return meta;
        }

        public List<StructureObject> getObjects() {
            return objects;
        }

        public List<Relation> getRelations() {
            return relations;
        }
    }
}
