package ru.mcp.structure.search;

import ru.mcp.structure.snapshot.StructureObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Построение TF-IDF векторов для объектов без внешнего API эмбеддингов.
 * Два представления на объект: «имя + синоним» и «тип + синоним» (или начало content) для RRF.
 * Поддержка: разбиение CamelCase, стоп-слова.
 */
public final class TfIdfVectors {

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_VOCAB_SIZE = 10_000;
    private static final int CONTENT_SNIPPET_CHARS = 500;

    /** Стоп-слова (русские и английские) — не попадают в словарь. */
    private static final Set<String> STOP_WORDS = Set.of(
            "в", "на", "не", "по", "для", "как", "это", "все", "его", "при", "или", "без", "под", "над", "из", "со", "до", "от",
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "had", "her", "was", "one", "our", "out", "has", "him", "how", "its", "may", "new", "now", "old", "see", "way", "who", "did", "get", "got", "let", "put", "say", "she", "too", "use"
    );

    /**
     * Строит словарь по всем объектам, заполняет у каждого объекта embeddingObjectName и embeddingFriendlyName.
     *
     * @param objects список объектов (будет изменён — записаны векторы)
     * @return модель для векторизации запроса при поиске
     */
    public static TfIdfModel compute(List<StructureObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return new TfIdfModel(0, Map.of(), new double[0]);
        }

        // Тексты для подсчёта document frequency: по два на объект (name+synonym, type+synonym/content)
        // splitCamelCase чтобы "ЗаказПациента" дало токены "заказ", "пациента"
        List<List<String>> docs = new ArrayList<>();
        for (StructureObject o : objects) {
            String textA = concat(o.getName(), o.getSynonym());
            String textB = concat(o.getType(), o.getSynonym());
            String content = o.getContent();
            if (content != null && !content.isBlank()) {
                String snippet = content.length() > CONTENT_SNIPPET_CHARS
                        ? content.substring(0, CONTENT_SNIPPET_CHARS) : content;
                textB = textB + " " + snippet;
            }
            docs.add(tokenizeForSearch(textA));
            docs.add(tokenizeForSearch(textB));
        }

        // Document frequency по всем терминам
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : docs) {
            for (String t : new ArrayList<>(new java.util.HashSet<>(doc))) {
                df.merge(t, 1, Integer::sum);
            }
        }

        // Убираем стоп-слова из словаря
        df.keySet().removeIf(t -> STOP_WORDS.contains(t.toLowerCase()));

        // Ограничиваем словарь по частоте (берём самые частые до MAX_VOCAB_SIZE)
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(df.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> termToId = new HashMap<>();
        int dim = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (dim >= MAX_VOCAB_SIZE) break;
            termToId.put(e.getKey(), dim++);
        }
        int dimension = dim;
        double[] idf = new double[dimension];
        double N = docs.size();
        for (Map.Entry<String, Integer> e : sorted) {
            Integer id = termToId.get(e.getKey());
            if (id != null) {
                idf[id] = Math.log(1.0 + N / (1.0 + e.getValue()));
            }
        }

        // Векторы для каждого объекта: два на объект
        for (int i = 0; i < objects.size(); i++) {
            StructureObject o = objects.get(i);
            List<String> docA = docs.get(2 * i);
            List<String> docB = docs.get(2 * i + 1);
            float[] vecA = tfIdfVector(docA, termToId, idf, dimension);
            float[] vecB = tfIdfVector(docB, termToId, idf, dimension);
            o.setEmbeddingObjectName(vecA);
            o.setEmbeddingFriendlyName(vecB);
        }

        return new TfIdfModel(dimension, Map.copyOf(termToId), idf);
    }

    private static String concat(String a, String b) {
        String x = a != null ? a.trim() : "";
        String y = b != null ? b.trim() : "";
        if (x.isEmpty()) return y;
        if (y.isEmpty()) return x;
        return x + " " + y;
    }

    /**
     * Разбивает CamelCase и точки: "ЗаказПациента" → "Заказ Пациента", "doc.Заказ" → "doc Заказ".
     * Пробел вставляется перед заглавной буквой, если перед ней строчная/цифра/подчёркивание, и после точки.
     */
    public static String splitCamelCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                out.append(' ');
                continue;
            }
            if (i > 0 && Character.isUpperCase(c)) {
                char prev = s.charAt(i - 1);
                if (Character.isLowerCase(prev) || Character.isDigit(prev) || prev == '_') {
                    out.append(' ');
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Токенизация с предварительным разбиением CamelCase (для запроса и для имени/синонима при поиске). */
    public static List<String> tokenizeForSearch(String text) {
        return tokenize(splitCamelCase(text));
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                cur.append(Character.toLowerCase(c));
            } else {
                if (cur.length() >= MIN_TERM_LENGTH) {
                    out.add(cur.toString());
                }
                cur.setLength(0);
            }
        }
        if (cur.length() >= MIN_TERM_LENGTH) {
            out.add(cur.toString());
        }
        return out;
    }

    private static float[] tfIdfVector(List<String> doc, Map<String, Integer> termToId, double[] idf, int dimension) {
        Map<Integer, Double> tf = new HashMap<>();
        for (String t : doc) {
            Integer id = termToId.get(t);
            if (id != null) {
                tf.merge(id, 1.0, Double::sum);
            }
        }
        float[] v = new float[dimension];
        for (Map.Entry<Integer, Double> e : tf.entrySet()) {
            int id = e.getKey();
            if (id >= 0 && id < dimension) {
                v[id] = (float) (e.getValue() * idf[id]);
            }
        }
        return normalize(v);
    }

    private static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm <= 1e-9) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }
}
