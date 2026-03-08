package ru.mcp.structure.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель TF-IDF: словарь термов и IDF, используется для векторизации запроса при поиске.
 */
public final class TfIdfModel {

    private final int dimension;
    private final Map<String, Integer> termToId;
    private final double[] idf;

    TfIdfModel(int dimension, Map<String, Integer> termToId, double[] idf) {
        this.dimension = dimension;
        this.termToId = termToId;
        this.idf = idf;
    }

    public int getDimension() {
        return dimension;
    }

    /** Векторизация текста запроса в TF-IDF вектор той же размерности. */
    public float[] vectorizeQuery(String text) {
        if (text == null || text.isBlank()) {
            float[] v = new float[dimension];
            return v;
        }
        List<String> terms = TfIdfVectors.tokenizeForSearch(text);
        Map<Integer, Double> tf = new HashMap<>();
        for (String t : terms) {
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
