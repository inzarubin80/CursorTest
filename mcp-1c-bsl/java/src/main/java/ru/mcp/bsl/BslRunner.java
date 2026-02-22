package ru.mcp.bsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Запуск BSL Language Server в режиме analyze или format (subprocess).
 * Требуется только JVM: BSL LS — отдельный JAR, тот же движок, что и в SonarQube BSL Plugin.
 * Поиск JAR: BSL_LANGUAGE_SERVER_JAR → bsl-language-server/bsl-language-server.jar (в проекте) → cwd/bsl-language-server.jar.
 */
public final class BslRunner {

    private static final String JAR_NAME = "bsl-language-server.jar";
    private static final String PROJECT_SERVER_DIR = "bsl-language-server";
    private static final String REPORT_FILE = "bsl-json.json";

    private final String jarPath;
    private final ObjectMapper json = new ObjectMapper();

    public BslRunner() {
        this(null);
    }

    /**
     * @param explicitJarPath путь к JAR BSL Language Server (из аргумента MCP или env); null = авто-поиск
     */
    public BslRunner(String explicitJarPath) {
        this.jarPath = explicitJarPath != null && !explicitJarPath.isBlank()
                ? explicitJarPath
                : resolveJarPath();
    }

    /**
     * Порядок поиска: 1) BSL_LANGUAGE_SERVER_JAR, 2) bsl-language-server/bsl-language-server.jar (от cwd),
     * 3) ../bsl-language-server/bsl-language-server.jar (если cwd = java/), 4) bsl-language-server.jar в cwd.
     */
    private static String resolveJarPath() {
        String env = System.getenv("BSL_LANGUAGE_SERVER_JAR");
        if (env != null && !env.isBlank()) {
            return env;
        }
        Path cwd = Paths.get("").toAbsolutePath();
        Path inProject = cwd.resolve(PROJECT_SERVER_DIR).resolve(JAR_NAME);
        if (Files.isRegularFile(inProject)) {
            return inProject.toString();
        }
        Path inProjectFromJava = cwd.resolve("..").resolve(PROJECT_SERVER_DIR).resolve(JAR_NAME).normalize();
        if (Files.isRegularFile(inProjectFromJava)) {
            return inProjectFromJava.toString();
        }
        Path inCwd = cwd.resolve(JAR_NAME);
        return inCwd.toString();
    }

    public String getJarPath() {
        return jarPath;
    }

    public boolean isJarAvailable() {
        return Files.isRegularFile(Paths.get(jarPath));
    }

    /**
     * Запуск анализа. srcDir — каталог или один файл .bsl/.os.
     * Возвращает текст отчёта (диагностики + метрики) или сообщение об ошибке.
     */
    public String analyze(String srcDir) {
        if (!isJarAvailable()) {
            return "Ошибка: JAR BSL Language Server не найден: " + jarPath
                    + ". Задайте BSL_LANGUAGE_SERVER_JAR или положите " + JAR_NAME + " в каталог bsl-language-server/ проекта или в текущую директорию.";
        }

        Path src = Paths.get(srcDir).toAbsolutePath();
        if (!Files.exists(src)) {
            return "Ошибка: путь не существует: " + src;
        }

        Path analyzeDir = Files.isDirectory(src) ? src : src.getParent();
        Path workDir = analyzeDir;
        Path outDir = workDir;

        List<String> command = List.of(
                "java", "-jar", jarPath,
                "analyze", "--srcDir", analyzeDir.toString(),
                "--reporter", "json", "-o", outDir.toString(), "-q"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean ok = p.waitFor(2, TimeUnit.MINUTES) && p.exitValue() == 0;

            if (!ok) {
                return "Ошибка запуска BSL LS (exit " + (p.exitValue()) + "): " + out;
            }

            Path reportPath = outDir.resolve(REPORT_FILE);
            if (!Files.isRegularFile(reportPath)) {
                return "Ошибка: отчёт не создан: " + reportPath + "\nВывод: " + out;
            }

            return formatReport(json.readTree(Files.readAllBytes(reportPath)), reportPath);
        } catch (IOException e) {
            return "Ошибка ввода-вывода: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Прервано: " + e.getMessage();
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    /**
     * Форматирование файла или каталога.
     */
    public String format(String src) {
        if (!isJarAvailable()) {
            return "Ошибка: JAR BSL Language Server не найден: " + jarPath
                    + ". Положите JAR в bsl-language-server/ или задайте BSL_LANGUAGE_SERVER_JAR.";
        }

        Path path = Paths.get(src).toAbsolutePath();
        if (!Files.exists(path)) {
            return "Ошибка: путь не существует: " + path;
        }

        List<String> command = List.of(
                "java", "-jar", jarPath,
                "format", "--src", path.toString(), "-q"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(path.getParent().toFile());
        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean ok = p.waitFor(2, TimeUnit.MINUTES) && p.exitValue() == 0;
            if (!ok) {
                return "Ошибка форматирования (exit " + p.exitValue() + "): " + out;
            }
            return "Форматирование выполнено успешно.";
        } catch (IOException e) {
            return "Ошибка: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Прервано: " + e.getMessage();
        }
    }

    private String formatReport(JsonNode root, Path reportPath) {
        StringBuilder b = new StringBuilder();
        b.append("Анализ: ").append(root.path("sourceDir").asText("")).append("\n");
        b.append("Дата: ").append(root.path("date").asText("")).append("\n\n");

        JsonNode fileInfos = root.get("fileinfos");
        if (fileInfos == null || !fileInfos.isArray()) {
            return b.toString();
        }

        int totalDiag = 0;
        for (JsonNode fi : fileInfos) {
            String path = fi.path("path").asText("");
            String shortPath = path;
            if (shortPath.startsWith("file:///")) {
                shortPath = shortPath.substring(7);
            }
            int last = shortPath.replace('\\', '/').lastIndexOf('/');
            if (last >= 0) {
                shortPath = shortPath.substring(last + 1);
            }

            JsonNode diags = fi.get("diagnostics");
            JsonNode metrics = fi.get("metrics");
            if ((diags == null || diags.isEmpty()) && metrics == null) {
                continue;
            }

            b.append("**").append(shortPath).append("**\n");

            if (metrics != null) {
                b.append("  Метрики: строк ").append(metrics.path("lines").asInt(0))
                        .append(", ncloc ").append(metrics.path("ncloc").asInt(0))
                        .append(", процедур ").append(metrics.path("procedures").asInt(0))
                        .append(", функций ").append(metrics.path("functions").asInt(0))
                        .append(", цикл. сложность ").append(metrics.path("cyclomaticComplexity").asInt(0))
                        .append(", когн. сложность ").append(metrics.path("cognitiveComplexity").asInt(0))
                        .append("\n");
            }

            if (diags != null && diags.isArray()) {
                for (JsonNode d : diags) {
                    totalDiag++;
                    JsonNode range = d.path("range");
                    int line = range.path("start").path("line").asInt(0) + 1;
                    int col = range.path("start").path("character").asInt(0) + 1;
                    b.append("  - [").append(d.path("severity").asText(""))
                            .append("] ").append(d.path("code").asText(""))
                            .append(" (").append(d.path("source").asText(""))
                            .append(") — строка ").append(line).append(", кол. ").append(col)
                            .append(": ").append(d.path("message").asText("")).append("\n");
                }
            }
            b.append("\n");
        }

        if (totalDiag == 0 && fileInfos.size() > 0) {
            b.append("Диагностик не найдено.\n");
        }

        return b.toString();
    }
}
