package ru.mcp.structure.snapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Загрузка снимка из ZIP в формате mcp-1c-v1 (RAG): objects.csv с колонками
 * "Имя объекта", "Тип объекта", "Синоним", "Файл" и markdown-файлы с описаниями.
 * Всё хранится в памяти, без векторной БД.
 *
 * @see <a href="https://github.com/FSerg/mcp-1c-v1">mcp-1c-v1</a>
 */
public final class RagZipLoader {

    private static final String CSV_NAME = "objects.csv";
    private static final String CSV_SEP = ";";

    private static final Map<String, String> TYPE_TO_PREFIX = new HashMap<>();
    private static final Map<String, String> TYPE_TO_ENGLISH = new HashMap<>();

    static {
        TYPE_TO_PREFIX.put("Документ", "doc");
        TYPE_TO_PREFIX.put("Справочник", "cat");
        TYPE_TO_PREFIX.put("ОбщийМодуль", "commonmodule");
        TYPE_TO_PREFIX.put("Общий модуль", "commonmodule");
        TYPE_TO_PREFIX.put("РегистрСведений", "inforeg");
        TYPE_TO_PREFIX.put("Регистр сведений", "inforeg");
        TYPE_TO_PREFIX.put("РегистрНакопления", "accreg");
        TYPE_TO_PREFIX.put("Регистр накопления", "accreg");
        TYPE_TO_PREFIX.put("РегистрБухгалтерии", "acctreg");
        TYPE_TO_PREFIX.put("Регистр бухгалтерии", "acctreg");
        TYPE_TO_PREFIX.put("Константа", "const");
        TYPE_TO_PREFIX.put("Перечисление", "enum");
        TYPE_TO_PREFIX.put("ПланВидовХарактеристик", "chartofcharacteristictypes");
        TYPE_TO_PREFIX.put("План видов характеристик", "chartofcharacteristictypes");
        TYPE_TO_PREFIX.put("Отчет", "report");
        TYPE_TO_PREFIX.put("Отчёт", "report");
        TYPE_TO_PREFIX.put("Обработка", "dataprocessor");
        TYPE_TO_PREFIX.put("ПланСчетов", "chartofaccounts");
        TYPE_TO_PREFIX.put("План счетов", "chartofaccounts");
        TYPE_TO_PREFIX.put("РегистрРасчета", "calculationregister");
        TYPE_TO_PREFIX.put("Регистр расчёта", "calculationregister");

        TYPE_TO_ENGLISH.put("Документ", "Document");
        TYPE_TO_ENGLISH.put("Справочник", "Catalog");
        TYPE_TO_ENGLISH.put("ОбщийМодуль", "CommonModule");
        TYPE_TO_ENGLISH.put("Общий модуль", "CommonModule");
        TYPE_TO_ENGLISH.put("РегистрСведений", "InformationRegister");
        TYPE_TO_ENGLISH.put("Регистр сведений", "InformationRegister");
        TYPE_TO_ENGLISH.put("РегистрНакопления", "AccumulationRegister");
        TYPE_TO_ENGLISH.put("Регистр накопления", "AccumulationRegister");
        TYPE_TO_ENGLISH.put("РегистрБухгалтерии", "AccountingRegister");
        TYPE_TO_ENGLISH.put("Регистр бухгалтерии", "AccountingRegister");
        TYPE_TO_ENGLISH.put("Константа", "Constant");
        TYPE_TO_ENGLISH.put("Перечисление", "Enum");
        TYPE_TO_ENGLISH.put("ПланВидовХарактеристик", "ChartOfCharacteristicTypes");
        TYPE_TO_ENGLISH.put("План видов характеристик", "ChartOfCharacteristicTypes");
        TYPE_TO_ENGLISH.put("Отчет", "Report");
        TYPE_TO_ENGLISH.put("Отчёт", "Report");
        TYPE_TO_ENGLISH.put("Обработка", "DataProcessor");
        TYPE_TO_ENGLISH.put("ПланСчетов", "ChartOfAccounts");
        TYPE_TO_ENGLISH.put("План счетов", "ChartOfAccounts");
        TYPE_TO_ENGLISH.put("РегистрРасчета", "CalculationRegister");
        TYPE_TO_ENGLISH.put("Регистр расчёта", "CalculationRegister");
    }

    /**
     * Загружает снимок из ZIP-архива в формате RAG (objects.csv + markdown).
     *
     * @param zipPath путь к ZIP-файлу
     * @return снимок (meta, objects, relations пустой)
     */
    public static SnapshotLoader.Snapshot load(Path zipPath) throws IOException {
        Path p = zipPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) {
            throw new IOException("Not a file: " + p);
        }

        Map<String, byte[]> zipEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(p), java.nio.charset.StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..")) continue;
                byte[] buf = readAllBytes(zis);
                zipEntries.put(name, buf);
                zipEntries.put(name.replace('\\', '/'), buf);
                int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (lastSlash >= 0) {
                    zipEntries.put(name.substring(lastSlash + 1), buf);
                }
                zis.closeEntry();
            }
        }

        String csvEntryName = null;
        for (String key : zipEntries.keySet()) {
            if (key.endsWith(CSV_NAME)) {
                csvEntryName = key;
                break;
            }
        }
        if (csvEntryName == null) {
            throw new IOException("В архиве не найден " + CSV_NAME);
        }

        byte[] csvBytes = zipEntries.get(csvEntryName);
        String csvRoot = csvEntryName.contains("/") ? csvEntryName.substring(0, csvEntryName.lastIndexOf('/') + 1) : "";
        if (csvRoot.isEmpty() && csvEntryName.contains("\\")) {
            csvRoot = csvEntryName.substring(0, csvEntryName.lastIndexOf('\\') + 1);
        }

        List<String[]> rows = parseCsv(new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new IOException(CSV_NAME + " пуст или неверный формат");
        }

        String[] header = rows.get(0);
        int idxName = indexOf(header, "Имя объекта");
        int idxType = indexOf(header, "Тип объекта");
        int idxSynonym = indexOf(header, "Синоним");
        int idxFile = indexOf(header, "Файл");
        if (idxName < 0 || idxType < 0 || idxSynonym < 0 || idxFile < 0) {
            throw new IOException(CSV_NAME + " должен содержать колонки: Имя объекта, Тип объекта, Синоним, Файл. Найдены: " + String.join(", ", header));
        }

        List<StructureObject> objects = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length <= Math.max(idxName, Math.max(idxType, Math.max(idxSynonym, idxFile)))) continue;
            String nameRaw = cell(row, idxName);
            String typeRu = cell(row, idxType);
            String synonym = cell(row, idxSynonym);
            String filePath = cell(row, idxFile);
            if (nameRaw == null || nameRaw.isBlank()) continue;

            // Имя объекта может быть "Справочник.AETitles" или "AETitles"
            String name = nameRaw.contains(".") ? nameRaw.substring(nameRaw.lastIndexOf('.') + 1) : nameRaw;
            String prefix = TYPE_TO_PREFIX.getOrDefault(typeRu.trim(), typeRu.trim().toLowerCase().replace(" ", ""));
            String typeEn = TYPE_TO_ENGLISH.getOrDefault(typeRu.trim(), typeRu);
            String id = prefix + "." + name;

            String content = "";
            if (filePath != null && !filePath.isBlank()) {
                String normalizedPath = filePath.replace('\\', '/');
                content = readEntry(zipEntries, csvRoot, normalizedPath);
            }

            StructureObject obj = new StructureObject();
            obj.setId(id);
            obj.setType(typeEn);
            obj.setName(name);
            obj.setSynonym(synonym != null ? synonym : "");
            obj.setDescription("");
            obj.setContent(content.isEmpty() ? null : content);
            obj.setProps(null);
            obj.setTabularSections(null);
            obj.setMovements(null);
            objects.add(obj);
        }

        Meta meta = new Meta();
        meta.setVersion("1.0");
        meta.setConfigName("RAG export");
        meta.setConfigVersion("");
        meta.setExportedAt("");
        meta.setSource("rag-zip");
        meta.setObjectCount(objects.size());
        meta.setIndexVersion(1);

        return new SnapshotLoader.Snapshot(meta, objects, Collections.emptyList());
    }

    private static final String BOM = "\uFEFF";

    private static int indexOf(String[] header, String col) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i].trim().replace(BOM, "");
            if (col.equals(h)) return i;
        }
        return -1;
    }

    private static String cell(String[] row, int idx) {
        if (idx >= row.length) return "";
        String s = row[idx];
        return s == null ? "" : s.trim();
    }

    private static String readEntry(Map<String, byte[]> zipEntries, String csvRoot, String filePath) {
        String path1 = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String path2 = csvRoot + path1;
        String path3 = path1.contains("/") ? path1 : csvRoot + path1;
        byte[] buf = zipEntries.get(path1);
        if (buf == null) buf = zipEntries.get(path2);
        if (buf == null) buf = zipEntries.get(path3);
        if (buf == null) buf = zipEntries.get(filePath);
        if (buf == null) {
            for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
                if (e.getKey().endsWith(path1) || e.getKey().endsWith("/" + path1)) {
                    buf = e.getValue();
                    break;
                }
            }
        }
        if (buf == null) return "";
        return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** Парсинг CSV с разделителем ; и полями в кавычках. */
    private static List<String[]> parseCsv(String csv) {
        List<String[]> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        List<String> row = new ArrayList<>();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                field.append(c);
            } else if (c == ';') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\r') {
                // skip
            } else if (c == '\n') {
                row.add(field.toString().trim());
                field.setLength(0);
                result.add(row.toArray(new String[0]));
                row.clear();
            } else {
                field.append(c);
            }
        }
        row.add(field.toString().trim());
        if (!row.isEmpty() && !row.stream().allMatch(String::isEmpty)) {
            result.add(row.toArray(new String[0]));
        }
        return result;
    }
}
