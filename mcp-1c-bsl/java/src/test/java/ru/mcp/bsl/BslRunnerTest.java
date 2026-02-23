package ru.mcp.bsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты BslRunner: проверка, что анализ и форматирование 1С/BSL
 * возвращают ожидаемые сообщения (с JAR BSL LS или без него).
 */
class BslRunnerTest {

    @Test
    void analyzeWhenJarMissingReturnsError() {
        BslRunner runner = new BslRunner();
        // Без BSL_LANGUAGE_SERVER_JAR и без bsl-language-server.jar в cwd
        if (runner.isJarAvailable()) {
            return; // в окружении есть JAR — пропускаем
        }
        String result = runner.analyze(System.getProperty("user.dir"));
        assertTrue(result.startsWith("Ошибка:"), "Должно быть сообщение об ошибке: " + result);
        assertTrue(result.contains("JAR") || result.contains("не найден"), result);
    }

    @Test
    void analyzeWhenPathMissingReturnsError() {
        BslRunner runner = new BslRunner();
        String result = runner.analyze("/nonexistent/path/12345");
        assertTrue(result.startsWith("Ошибка:"), result);
        // Сначала проверяется JAR: если его нет — ошибка про JAR; иначе — про путь
        boolean pathError = result.contains("не существует") || result.contains("путь");
        boolean jarError = result.contains("JAR") || result.contains("не найден");
        assertTrue(pathError || jarError, result);
    }

    @Test
    void formatWhenJarMissingReturnsError() {
        BslRunner runner = new BslRunner();
        if (runner.isJarAvailable()) {
            return;
        }
        String result = runner.format(System.getProperty("user.dir"));
        assertTrue(result.startsWith("Ошибка:"), result);
    }

    @Test
    void formatWhenPathMissingReturnsError() {
        BslRunner runner = new BslRunner();
        String result = runner.format("/nonexistent/file.bsl");
        assertTrue(result.startsWith("Ошибка:"), result);
        boolean pathError = result.contains("не существует") || result.contains("путь");
        boolean jarError = result.contains("JAR") || result.contains("не найден");
        assertTrue(pathError || jarError, result);
    }

    @Test
    void analyzeWithRealSampleDir_returnsAnalysisOrError(@TempDir Path tempDir) throws Exception {
        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        Path sampleFile = bslDir.resolve("sample.bsl");
        Files.writeString(sampleFile, "Функция Тест()\n    Возврат 1;\nКонецФункции\n");

        BslRunner runner = new BslRunner();
        String result = runner.analyze(bslDir.toString());

        // Либо успешный отчёт (если BSL LS доступен), либо ошибка "JAR не найден"
        assertNotNull(result);
        assertFalse(result.isBlank());
        boolean success = result.contains("Анализ") || result.contains("Метрики") || result.contains("Диагностик");
        boolean noJar = result.startsWith("Ошибка:") && result.contains("JAR");
        assertTrue(success || noJar, "Ожидается отчёт анализа или сообщение об отсутствии JAR: " + result);
    }

    // 5.3 — успешный анализ при наличии JAR: структура отчёта (Анализ, Дата, метрики)
    @Test
    void analyzeWhenJarAvailable_returnsReportWithStructure(@TempDir Path tempDir) throws Exception {
        BslRunner runner = new BslRunner();
        Assumptions.assumeTrue(runner.isJarAvailable(), "JAR BSL LS требуется для теста");

        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        Path sampleFile = bslDir.resolve("sample.bsl");
        Files.writeString(sampleFile, "Функция Тест()\n    Возврат 1;\nКонецФункции\n");

        String result = runner.analyze(bslDir.toString());
        assertFalse(result.startsWith("Ошибка:"), result);
        assertTrue(result.contains("Анализ"), result);
        assertTrue(result.contains("Дата"), result);
        assertTrue(result.contains("Метрики") || result.contains("**"), "Ожидается блок по файлу с метриками или именем файла");
        assertTrue(result.contains("строк") || result.contains("ncloc") || result.contains("процедур") || result.contains("функций")
                || result.contains("сложность"), "Ожидаются метрики (lines/ncloc/procedures/functions/complexity): " + result);
    }

    // 5.4 — анализ по пути к одному файлу (не каталогу)
    @Test
    void analyzeSingleFilePath_returnsReport(@TempDir Path tempDir) throws Exception {
        BslRunner runner = new BslRunner();
        Assumptions.assumeTrue(runner.isJarAvailable(), "JAR BSL LS требуется для теста");

        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        Path sampleFile = bslDir.resolve("sample.bsl");
        Files.writeString(sampleFile, "Функция Тест()\n    Возврат 1;\nКонецФункции\n");

        String result = runner.analyze(sampleFile.toAbsolutePath().toString());
        assertFalse(result.startsWith("Ошибка:"), result);
        assertTrue(result.contains("Анализ") || result.contains("Метрики") || result.contains("Диагностик"), result);
    }

    // 6.3 — успешное форматирование при наличии JAR
    @Test
    void formatWhenJarAvailable_returnsSuccess(@TempDir Path tempDir) throws Exception {
        BslRunner runner = new BslRunner();
        Assumptions.assumeTrue(runner.isJarAvailable(), "JAR BSL LS требуется для теста");

        Path bslDir = tempDir.resolve("bsl");
        Files.createDirectories(bslDir);
        Path sampleFile = bslDir.resolve("sample.bsl");
        Files.writeString(sampleFile, "Функция Тест()\nВозврат 1;\nКонецФункции\n");

        String result = runner.format(sampleFile.toAbsolutePath().toString());
        assertFalse(result.startsWith("Ошибка:"), result);
        assertTrue(result.contains("Форматирование выполнено успешно") || result.contains("успешно"), result);
    }

    // 2.4 — явный путь к JAR в конструкторе
    @Test
    void explicitJarPathInConstructor_isReturnedByGetJarPath() {
        String customPath = "/path/to/custom.jar";
        BslRunner runner = new BslRunner(customPath);
        assertEquals(customPath, runner.getJarPath());
    }

    // --- formatReport (7.1–7.5) ---
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void formatReport_emptyFileinfos_returnsOnlyAnalysisAndDate() throws Exception {
        BslRunner runner = new BslRunner();
        ObjectNode root = JSON.createObjectNode();
        root.put("sourceDir", "/proj");
        root.put("date", "2025-01-01");
        root.putArray("fileinfos");

        String out = runner.formatReport(root, null);
        assertTrue(out.contains("Анализ"));
        assertTrue(out.contains("Дата"));
        assertTrue(out.contains("/proj"));
        assertFalse(out.contains("**"), "Не должно быть блоков по файлам");
    }

    @Test
    void formatReport_fileWithoutDiagnosticsOrMetrics_skipped() throws Exception {
        BslRunner runner = new BslRunner();
        ObjectNode root = JSON.createObjectNode();
        root.put("sourceDir", "");
        root.put("date", "");
        ArrayNode fileinfos = root.putArray("fileinfos");
        ObjectNode fi = fileinfos.addObject();
        fi.put("path", "file:///C:/proj/empty.bsl");
        // no diagnostics, no metrics -> skip

        String out = runner.formatReport(root, null);
        assertTrue(out.contains("Анализ"));
        assertFalse(out.contains("**empty.bsl**"), "Файл без диагностик и метрик должен быть пропущен");
    }

    @Test
    void formatReport_fileUriPath_shortPathIsFileName() throws Exception {
        BslRunner runner = new BslRunner();
        ObjectNode root = JSON.createObjectNode();
        root.put("sourceDir", "");
        root.put("date", "");
        ArrayNode fileinfos = root.putArray("fileinfos");
        ObjectNode fi = fileinfos.addObject();
        fi.put("path", "file:///C:/proj/src/module.bsl");
        ObjectNode metrics = fi.putObject("metrics");
        metrics.put("lines", 10);
        metrics.put("ncloc", 8);
        metrics.put("procedures", 0);
        metrics.put("functions", 1);
        metrics.put("cyclomaticComplexity", 1);
        metrics.put("cognitiveComplexity", 0);

        String out = runner.formatReport(root, null);
        assertTrue(out.contains("**module.bsl**"), "shortPath должен быть только имя файла: " + out);
        assertTrue(out.contains("Метрики"));
        assertTrue(out.contains("строк") && out.contains("ncloc"));
    }

    @Test
    void formatReport_diagnosticsWithRange_severityCodeMessageLineCol() throws Exception {
        BslRunner runner = new BslRunner();
        ObjectNode root = JSON.createObjectNode();
        root.put("sourceDir", "");
        root.put("date", "");
        ArrayNode fileinfos = root.putArray("fileinfos");
        ObjectNode fi = fileinfos.addObject();
        fi.put("path", "file:///C:/a.bsl");
        ArrayNode diags = fi.putArray("diagnostics");
        ObjectNode d = diags.addObject();
        d.put("severity", "info");
        d.put("code", "SomeRule");
        d.put("source", "BSL");
        d.put("message", "Подсказка");
        ObjectNode range = d.putObject("range");
        range.putObject("start").put("line", 1).put("character", 0);

        String out = runner.formatReport(root, null);
        assertTrue(out.contains("**a.bsl**"));
        assertTrue(out.contains("info") && out.contains("SomeRule") && out.contains("Подсказка"));
        assertTrue(out.contains("строка 2") || out.contains("кол."), "Ожидаются строка/колонка (line+1): " + out);
    }

    @Test
    void formatReport_noDiagnosticsButHasFileinfos_appendsNoDiagnosticsMessage() throws Exception {
        BslRunner runner = new BslRunner();
        ObjectNode root = JSON.createObjectNode();
        root.put("sourceDir", "");
        root.put("date", "");
        ArrayNode fileinfos = root.putArray("fileinfos");
        ObjectNode fi = fileinfos.addObject();
        fi.put("path", "x.bsl");
        fi.putObject("metrics").put("lines", 5).put("ncloc", 4).put("procedures", 0).put("functions", 0)
                .put("cyclomaticComplexity", 0).put("cognitiveComplexity", 0);
        // no diagnostics

        String out = runner.formatReport(root, null);
        assertTrue(out.contains("Диагностик не найдено"), out);
    }
}
