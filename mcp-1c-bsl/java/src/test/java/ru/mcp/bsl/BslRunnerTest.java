package ru.mcp.bsl;

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
}
