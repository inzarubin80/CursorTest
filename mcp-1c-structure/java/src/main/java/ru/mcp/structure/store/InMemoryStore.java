package ru.mcp.structure.store;

import org.apache.commons.text.similarity.LevenshteinDistance;
import ru.mcp.structure.snapshot.Meta;
import ru.mcp.structure.snapshot.SnapshotLoader;
import ru.mcp.structure.snapshot.StructureObject;
import ru.mcp.structure.search.TfIdfModel;
import ru.mcp.structure.search.TfIdfVectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Хранилище снимка структуры 1С в памяти. Данные загружаются из XML (СтруктураБазыДанных.xml).
 * Поддерживает нечёткий поиск по имени/синониму.
 */
public final class InMemoryStore {

    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();
    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final int RRF_K = 60;

    private volatile Meta meta;
    private volatile Map<String, StructureObject> objectsById;
    private volatile TfIdfModel tfIdfModel;

    public InMemoryStore() {
        this.meta = null;
        this.objectsById = Map.of();
    }

    public synchronized void load(SnapshotLoader.Snapshot snapshot) {
        this.meta = snapshot.getMeta();
        Map<String, StructureObject> map = new HashMap<>();
        for (StructureObject o : snapshot.getObjects()) {
            if (o != null && o.getId() != null) {
                map.put(normalizeId(o.getId()), o);
            }
        }
        this.objectsById = Map.copyOf(map);

        this.tfIdfModel = TfIdfVectors.compute(snapshot.getObjects());
    }

    public Meta getMeta() {
        return meta;
    }

    public boolean isLoaded() {
        return meta != null && !objectsById.isEmpty();
    }

    /**
     * Нечёткий поиск: при наличии TF-IDF векторов — векторный поиск (косинусная близость) + RRF по двум представлениям;
     * иначе подстрока по name/synonym и ранжирование по Левенштейну.
     */
    public SearchResult search(String query, String typeFilter, int limit, int offset) {
        if (query == null || query.isBlank()) {
            return new SearchResult(0, List.of());
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        String type = typeFilter != null && !typeFilter.isBlank() ? typeFilter.trim().toLowerCase(Locale.ROOT) : "";

        int lim = limit <= 0 ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT);
        int off = Math.max(0, offset);

        if (tfIdfModel != null && tfIdfModel.getDimension() > 0) {
            return searchVector(q, type, lim, off);
        }
        return searchText(q, type, lim, off);
    }

    /** Векторный поиск + текстовое ранжирование: RRF по трём спискам (objectName, friendlyName, текст). */
    private SearchResult searchVector(String q, String typeFilter, int limit, int offset) {
        float[] queryVec = tfIdfModel.vectorizeQuery(q);
        List<StructureObject> candidates = new ArrayList<>();
        for (StructureObject o : objectsById.values()) {
            if (o == null) continue;
            if (!typeFilter.isEmpty() && !typeFilter.equals(o.getType() != null ? o.getType().toLowerCase(Locale.ROOT) : "")) {
                continue;
            }
            if (o.getEmbeddingObjectName() != null && o.getEmbeddingFriendlyName() != null) {
                candidates.add(o);
            }
        }
        if (candidates.isEmpty()) {
            return new SearchResult(0, List.of());
        }
        List<String> queryTerms = TfIdfVectors.tokenizeForSearch(q);
        double[] cosName = new double[candidates.size()];
        double[] cosFriendly = new double[candidates.size()];
        double[] textScore = new double[candidates.size()];
        double[] overlapScore = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            StructureObject o = candidates.get(i);
            cosName[i] = cosine(queryVec, o.getEmbeddingObjectName());
            cosFriendly[i] = cosine(queryVec, o.getEmbeddingFriendlyName());
            textScore[i] = textScoreForQuery(q, o);
            overlapScore[i] = tokenOverlapScore(queryTerms, o);
        }
        int[] rankByName = argsortDesc(cosName);
        int[] rankByFriendly = argsortDesc(cosFriendly);
        int[] rankByText = argsortDesc(textScore);
        int[] rankByOverlap = argsortDesc(overlapScore);
        int[] rankPosByName = new int[candidates.size()];
        int[] rankPosByFriendly = new int[candidates.size()];
        int[] rankPosByText = new int[candidates.size()];
        int[] rankPosByOverlap = new int[candidates.size()];
        for (int r = 0; r < rankByName.length; r++) {
            rankPosByName[rankByName[r]] = r;
            rankPosByFriendly[rankByFriendly[r]] = r;
            rankPosByText[rankByText[r]] = r;
            rankPosByOverlap[rankByOverlap[r]] = r;
        }
        List<ScoredObject> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            // Двойной вес ранга по имени; четвёртый сигнал — доля совпавших токенов
            double rrf = 2.0 / (RRF_K + rankPosByName[i] + 1)
                    + 1.0 / (RRF_K + rankPosByFriendly[i] + 1)
                    + 1.0 / (RRF_K + rankPosByText[i] + 1)
                    + 1.0 / (RRF_K + rankPosByOverlap[i] + 1);
            scored.add(new ScoredObject(candidates.get(i), rrf));
        }
        scored.sort(Comparator.comparingDouble((ScoredObject s) -> s.score).reversed());
        int total = scored.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<StructureObject> page = new ArrayList<>();
        for (int i = from; i < to; i++) {
            page.add(scored.get(i).obj);
        }
        return new SearchResult(total, page);
    }

    /** Доля токенов запроса, встретившихся в имени/синониме объекта (для RRF). */
    private static double tokenOverlapScore(List<String> queryTerms, StructureObject o) {
        if (queryTerms == null || queryTerms.isEmpty()) return 0;
        String name = o.getName() != null ? o.getName() : "";
        String synonym = o.getSynonym() != null ? o.getSynonym() : "";
        List<String> objectTerms = TfIdfVectors.tokenizeForSearch(name + " " + synonym);
        Set<String> objectSet = new HashSet<>(objectTerms);
        long matched = queryTerms.stream().filter(objectSet::contains).count();
        return (double) matched / queryTerms.size();
    }

    /** Оценка по тексту (подстрока + Левенштейн) для использования в RRF. */
    private double textScoreForQuery(String q, StructureObject o) {
        String name = o.getName() != null ? o.getName() : "";
        String synonym = o.getSynonym() != null ? o.getSynonym() : "";
        String nameL = name.toLowerCase(Locale.ROOT);
        String synonymL = synonym.toLowerCase(Locale.ROOT);
        String content = o.getContent() != null ? o.getContent().toLowerCase(Locale.ROOT) : "";
        if (nameL.contains(q) || synonymL.contains(q)) {
            int d1 = LEVENSHTEIN.apply(q, name);
            int d2 = LEVENSHTEIN.apply(q, synonym);
            return 1.0 / (1.0 + Math.min(d1, d2));
        }
        if (!content.isEmpty() && content.contains(q)) {
            return 0.2;
        }
        return 0;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static int[] argsortDesc(double[] x) {
        int[] idx = new int[x.length];
        for (int i = 0; i < x.length; i++) idx[i] = i;
        for (int i = 0; i < x.length; i++) {
            for (int j = i + 1; j < x.length; j++) {
                if (x[idx[j]] > x[idx[i]]) {
                    int t = idx[i];
                    idx[i] = idx[j];
                    idx[j] = t;
                }
            }
        }
        return idx;
    }

    /** Текстовый поиск: подстрока по name/synonym/content + Левенштейн. */
    private SearchResult searchText(String q, String type, int limit, int offset) {
        List<StructureObject> candidates = new ArrayList<>();
        for (StructureObject o : objectsById.values()) {
            if (o == null) continue;
            if (!type.isEmpty() && !type.equals(o.getType() != null ? o.getType().toLowerCase(Locale.ROOT) : "")) {
                continue;
            }
            String name = o.getName() != null ? o.getName().toLowerCase(Locale.ROOT) : "";
            String synonym = o.getSynonym() != null ? o.getSynonym().toLowerCase(Locale.ROOT) : "";
            String content = o.getContent() != null ? o.getContent().toLowerCase(Locale.ROOT) : "";
            if (name.contains(q) || synonym.contains(q) || (!content.isEmpty() && content.contains(q))) {
                candidates.add(o);
            }
        }
        List<ScoredObject> scored = new ArrayList<>();
        for (StructureObject o : candidates) {
            String name = o.getName() != null ? o.getName() : "";
            String synonym = o.getSynonym() != null ? o.getSynonym() : "";
            String nameL = name.toLowerCase(Locale.ROOT);
            String synonymL = synonym.toLowerCase(Locale.ROOT);
            double score;
            if (nameL.contains(q) || synonymL.contains(q)) {
                int d1 = LEVENSHTEIN.apply(q, name);
                int d2 = LEVENSHTEIN.apply(q, synonym);
                int dist = Math.min(d1, d2);
                score = 1.0 / (1.0 + dist);
            } else {
                score = 0.2;
            }
            scored.add(new ScoredObject(o, score));
        }
        scored.sort(Comparator.comparingDouble((ScoredObject s) -> s.score).reversed());
        int total = scored.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<StructureObject> page = new ArrayList<>();
        for (int i = from; i < to; i++) {
            page.add(scored.get(i).obj);
        }
        return new SearchResult(total, page);
    }

    public StructureObject getObject(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return null;
        }
        return objectsById.get(normalizeId(objectId));
    }

    public List<TypeCount> listTypes() {
        Map<String, Long> counts = new HashMap<>();
        for (StructureObject o : objectsById.values()) {
            if (o != null && o.getType() != null) {
                counts.merge(o.getType(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TypeCount(e.getKey(), e.getValue()))
                .toList();
    }

    private static String normalizeId(String id) {
        if (id == null) return "";
        id = id.trim();
        int dot = id.indexOf('.');
        if (dot > 0) {
            String prefix = id.substring(0, dot).toLowerCase(Locale.ROOT);
            String name = id.substring(dot + 1);
            String shortPrefix = switch (prefix) {
                case "document" -> "doc";
                case "catalog" -> "cat";
                case "commonmodule" -> "commonmodule";
                case "report" -> "report";
                case "dataprocessor" -> "dataprocessor";
                case "informationregister" -> "inforeg";
                case "accumulationregister" -> "accreg";
                case "accountingregister" -> "acctreg";
                default -> prefix;
            };
            return shortPrefix + "." + name;
        }
        return id;
    }

    private static final class ScoredObject {
        final StructureObject obj;
        final double score;

        ScoredObject(StructureObject obj, double score) {
            this.obj = obj;
            this.score = score;
        }
    }

    public static final class SearchResult {
        private final int total;
        private final List<StructureObject> matches;

        public SearchResult(int total, List<StructureObject> matches) {
            this.total = total;
            this.matches = List.copyOf(matches);
        }

        public int getTotal() {
            return total;
        }

        public List<StructureObject> getMatches() {
            return matches;
        }
    }

    public static final class TypeCount {
        private final String type;
        private final long count;

        public TypeCount(String type, long count) {
            this.type = type;
            this.count = count;
        }

        public String getType() {
            return type;
        }

        public long getCount() {
            return count;
        }
    }
}
