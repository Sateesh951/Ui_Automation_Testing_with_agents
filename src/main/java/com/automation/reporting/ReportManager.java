package com.automation.reporting;

import com.automation.testdata.TestCaseDefinition;
import com.automation.testdata.CaseMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ReportManager {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm-ss");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ThreadLocal<ExtentTest> CURRENT_TEST = new ThreadLocal<>();
    private static final Map<ITestResult, TestExecutionRecord> RECORDS_BY_RESULT = new ConcurrentHashMap<>();
    private static final Map<String, ExtentTest> CUCUMBER_TESTS = new ConcurrentHashMap<>();
    private static final Map<String, String> CUCUMBER_SCREENSHOTS = new ConcurrentHashMap<>();
    private static final List<TestExecutionRecord> RECORDS = new CopyOnWriteArrayList<>();
    private static ExtentReports extent;
    private static Path reportDirectory;
    private static Instant startedAt;

    private ReportManager() {}

    public static synchronized void start(String reportsRoot) {
        startedAt = Instant.now();
        ZonedDateTime now = ZonedDateTime.now(REPORT_ZONE);
        LocalDate date = now.toLocalDate();
        Path dateDirectory = Path.of(reportsRoot).resolve(DATE.format(date));
        try {
            Files.createDirectories(dateDirectory);
            String base = TIME.format(now.toLocalTime());
            Path candidate = dateDirectory.resolve(base);
            int suffix = 1;
            while (Files.exists(candidate)) candidate = dateDirectory.resolve(base + "-" + suffix++);
            Files.createDirectory(candidate);
            reportDirectory = candidate.toAbsolutePath();
            Files.createDirectories(reportDirectory.resolve("UI").resolve("screenshots"));
            Files.createDirectories(reportDirectory.resolve("API").resolve("request-response-logs"));
            Files.createDirectories(reportDirectory.resolve("logs"));
            System.setProperty("framework.report.dir", reportDirectory.toString());
            System.setProperty("framework.log.file", reportDirectory.resolve("logs")
                    .resolve("execution.log").toString());
            ExtentSparkReporter reporter = new ExtentSparkReporter(
                    reportDirectory.resolve("extent-report.html").toString());
            reporter.config().setDocumentTitle("Automation Execution Report");
            reporter.config().setReportName("UI and API Automation");
            extent = new ExtentReports();
            extent.attachReporter(reporter);
            extent.setSystemInfo("Execution started", startedAt.toString());
            extent.setSystemInfo("Browser", com.automation.config.FrameworkConfig.get("browser", "chromium"));
            RECORDS.clear();
            RECORDS_BY_RESULT.clear();
            CUCUMBER_TESTS.clear();
            CUCUMBER_SCREENSHOTS.clear();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize the execution report directory.", exception);
        }
    }

    public static Path reportDirectory() {
        if (reportDirectory == null) throw new IllegalStateException("Report manager has not started.");
        return reportDirectory;
    }

    public static void onStart(ITestResult result) {
        TestCaseDefinition testCase = caseFrom(result);
        CaseMetadata metadata = metadataFrom(result);
        TestExecutionRecord record = new TestExecutionRecord();
        record.module = testCase != null ? text(testCase.module, "Unknown")
                : metadata != null ? metadata.module() : "Unknown";
        record.testCaseId = testCase != null ? text(testCase.id, result.getMethod().getMethodName())
                : metadata != null ? metadata.id() : result.getMethod().getMethodName();
        record.description = testCase != null ? text(testCase.description, "")
                : metadata != null ? metadata.description() : result.getMethod().getMethodName();
        record.testType = testCase != null ? text(testCase.type, "Unknown").toUpperCase()
                : metadata != null ? metadata.type().toUpperCase() : "Unknown";
        record.expectedResult = testCase != null ? text(testCase.expectedResult, "")
                : metadata != null ? metadata.expectedResult() : "";
        record.status = "RUNNING";
        record.startedAt = Instant.now().toString();
        RECORDS.add(record);
        RECORDS_BY_RESULT.put(result, record);
        ExtentTest test = extent.createTest(record.testCaseId + " - " + record.description)
                .assignCategory(record.module, record.testType);
        if (!record.expectedResult.isBlank()) test.info("Expected: " + record.expectedResult);
        test.info("Module: " + record.module + " | Case: " + record.testCaseId
                + " | Type: " + record.testType);
        CURRENT_TEST.set(test);
    }

    public static void onSuccess(ITestResult result) {
        TestExecutionRecord record = update(result, "PASS");
        record.actualResult = "All configured assertions passed.";
        current().pass(record.actualResult);
        finish(result);
    }

    public static void onFailure(ITestResult result) {
        TestExecutionRecord record = update(result, "FAIL");
        Throwable throwable = result.getThrowable();
        record.failureReason = throwable == null ? "Test failed." : text(throwable.getMessage(), throwable.toString());
        record.exceptionDetails = stackTrace(throwable);
        record.actualResult = "One or more assertions or test steps failed.";
        ExtentTest test = current();
        test.fail(record.failureReason).fail(record.exceptionDetails);
        Object consoleErrors = result.getAttribute("console.errors");
        if (consoleErrors != null) test.warning("Browser console errors: " + consoleErrors);
        Object screenshot = result.getAttribute("screenshot");
        if (screenshot instanceof String path && !path.isBlank()) {
            test.addScreenCaptureFromPath(path, "Failure screenshot");
        }
        Object apiLog = result.getAttribute("api.log");
        if (apiLog instanceof String contents && !contents.isBlank()) {
            Path path = reportDirectory.resolve("API").resolve("request-response-logs")
                    .resolve(safeName(record.testCaseId) + ".log");
            try {
                Files.writeString(path, contents, StandardCharsets.UTF_8);
                test.info("API failure request/response log: " + path);
            } catch (IOException exception) {
                test.warning("Unable to save API failure log: " + exception.getMessage());
            }
        }
        finish(result);
    }

    public static void onSkipped(ITestResult result) {
        TestExecutionRecord record = update(result, "SKIP");
        Throwable throwable = result.getThrowable();
        record.actualResult = throwable == null ? "Skipped." : text(throwable.getMessage(), "Skipped.");
        record.failureReason = record.actualResult;
        current().skip(record.actualResult);
        finish(result);
    }

    public static synchronized void recordCucumberScenario(String module, String testCaseId,
                                                            String description, String status,
                                                            long durationMs, String failureReason,
                                                            String screenshot) {
        TestExecutionRecord record = new TestExecutionRecord();
        record.module = text(module, "Cucumber");
        record.testCaseId = text(testCaseId, "Cucumber scenario");
        record.description = text(description, record.testCaseId);
        record.testType = "UI";
        record.status = switch (status) {
            case "PASSED" -> "PASS";
            case "SKIPPED", "PENDING", "UNDEFINED", "UNUSED" -> "SKIP";
            default -> "FAIL";
        };
        record.startedAt = Instant.now().minusMillis(Math.max(0, durationMs)).toString();
        record.finishedAt = Instant.now().toString();
        record.durationMs = Math.max(0, durationMs);
        record.expectedResult = "All Gherkin steps pass.";
        record.actualResult = "Cucumber scenario " + status.toLowerCase() + ".";
        record.failureReason = failureReason;
        RECORDS.add(record);

        ExtentTest test = extent.createTest(record.testCaseId + " - " + record.description)
                .assignCategory(record.module, record.testType);
        CUCUMBER_TESTS.put(record.description, test);
        test.info("Expected: " + record.expectedResult);
        if ("PASS".equals(record.status)) {
            test.pass(record.actualResult);
        } else if ("SKIP".equals(record.status)) {
            test.skip(failureReason == null ? record.actualResult : failureReason);
        } else {
            test.fail(text(failureReason, record.actualResult));
            String screenshotPath = text(screenshot, CUCUMBER_SCREENSHOTS.get(description));
            if (!screenshotPath.isBlank()) {
                test.addScreenCaptureFromPath(reportDirectory.resolve(screenshotPath).toString(),
                        "Cucumber failure screenshot");
            }
        }
    }

    public static synchronized void attachCucumberScreenshot(String description, String screenshot) {
        CUCUMBER_SCREENSHOTS.put(description, screenshot);
        ExtentTest test = CUCUMBER_TESTS.get(description);
        if (test != null) {
            test.addScreenCaptureFromPath(reportDirectory.resolve(screenshot).toString(),
                    "Cucumber failure screenshot");
        }
    }

    public static synchronized void linkCucumberScreenshots() {
        Path jsonPath = reportDirectory.resolve("cucumber-step-report.json");
        Path screenshotsDirectory = reportDirectory.resolve("UI").resolve("screenshots");
        if (!Files.exists(jsonPath) || !Files.isDirectory(screenshotsDirectory)) return;
        try {
            List<Map<String, Object>> scenarios = MAPPER.readValue(jsonPath.toFile(),
                    new TypeReference<>() {});
            Path htmlPath = reportDirectory.resolve("cucumber-step-report.html");
            String html = Files.exists(htmlPath)
                    ? Files.readString(htmlPath, StandardCharsets.UTF_8) : "";
            for (Map<String, Object> scenario : scenarios) {
                if (!"FAILED".equals(scenario.get("status")) || scenario.get("screenshot") != null) continue;
                String name = String.valueOf(scenario.get("name"));
                String prefix = safeName(name) + "-";
                Path screenshot;
                try (var files = Files.list(screenshotsDirectory)) {
                    screenshot = files.filter(path -> path.getFileName().toString().startsWith(prefix))
                            .max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()))
                            .orElse(null);
                }
                if (screenshot == null) continue;
                String relativePath = "UI/screenshots/" + screenshot.getFileName();
                scenario.put("screenshot", relativePath);
                String heading = "<h2>" + escape(name) + "</h2>";
                int headingIndex = html.indexOf(heading);
                int paragraphEnd = headingIndex < 0 ? -1 : html.indexOf("</p>", headingIndex);
                if (paragraphEnd >= 0) {
                    String link = "<p><a href=\"" + escape(relativePath)
                            + "\">View failure screenshot</a></p>";
                    html = html.substring(0, paragraphEnd + 4) + link
                            + html.substring(paragraphEnd + 4);
                }
            }
            MAPPER.writeValue(jsonPath.toFile(), scenarios);
            if (!html.isBlank()) Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to link Cucumber failure screenshots.", exception);
        }
    }

    public static synchronized void finishReport() {
        if (extent == null) return;
        extent.flush();
        Instant endedAt = Instant.now();
        try {
            Map<String, Object> summary = summary(endedAt);
            MAPPER.writeValue(reportDirectory.resolve("execution-summary.json").toFile(), summary);
            Files.writeString(reportDirectory.resolve("execution-summary.html"),
                    dashboard(summary), StandardCharsets.UTF_8);
            Files.writeString(reportDirectory.resolve("index.html"),
                    dashboard(summary), StandardCharsets.UTF_8);
            Files.writeString(reportDirectory.resolve("UI").resolve("ui-test-results.html"),
                    resultsPage("UI"), StandardCharsets.UTF_8);
            Files.writeString(reportDirectory.resolve("API").resolve("api-test-results.html"),
                    resultsPage("API"), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write execution summary files.", exception);
        }
    }

    private static TestExecutionRecord update(ITestResult result, String status) {
        TestExecutionRecord record = RECORDS_BY_RESULT.get(result);
        if (record == null) {
            onStart(result);
            record = RECORDS_BY_RESULT.get(result);
        }
        record.status = status;
        record.durationMs = Math.max(0, result.getEndMillis() - result.getStartMillis());
        record.finishedAt = Instant.now().toString();
        return record;
    }

    private static void finish(ITestResult result) {
        CURRENT_TEST.remove();
        RECORDS_BY_RESULT.remove(result);
        com.automation.api.ApiLogCapture.clear();
    }

    private static ExtentTest current() {
        ExtentTest test = CURRENT_TEST.get();
        if (test == null) throw new IllegalStateException("No active Extent test on this thread.");
        return test;
    }

    private static TestCaseDefinition caseFrom(ITestResult result) {
        for (Object parameter : result.getParameters()) {
            if (parameter instanceof TestCaseDefinition testCase) return testCase;
        }
        return null;
    }

    private static CaseMetadata metadataFrom(ITestResult result) {
        java.lang.reflect.Method method = result.getMethod().getConstructorOrMethod().getMethod();
        return method == null ? null : method.getAnnotation(CaseMetadata.class);
    }

    private static Map<String, Object> summary(Instant endedAt) {
        long passed = count("PASS");
        long failed = count("FAIL");
        long skipped = count("SKIP");
        long total = passed + failed + skipped;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startedAt", startedAt.toString());
        summary.put("finishedAt", endedAt.toString());
        summary.put("durationMs", endedAt.toEpochMilli() - startedAt.toEpochMilli());
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", skipped);
        summary.put("passPercentage", total == 0 ? 0.0 : Math.round(passed * 10000.0 / total) / 100.0);
        summary.put("moduleSummary", groupBy("module"));
        summary.put("typeSummary", groupBy("testType"));
        summary.put("tests", new ArrayList<>(RECORDS));
        return summary;
    }

    private static long count(String status) {
        return RECORDS.stream().filter(record -> status.equals(record.status)).count();
    }

    private static Map<String, Map<String, Long>> groupBy(String field) {
        Map<String, Map<String, Long>> groups = new LinkedHashMap<>();
        for (TestExecutionRecord record : RECORDS) {
            String key = "module".equals(field) ? record.module : record.testType;
            groups.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .merge(record.status, 1L, Long::sum);
        }
        return groups;
    }

    private static String dashboard(Map<String, Object> summary) throws IOException {
        String json = MAPPER.writeValueAsString(summary);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Execution summary</title>"
                + "<style>body{font:15px Arial;margin:2rem;color:#222}table{border-collapse:collapse}"
                + "td,th{border:1px solid #bbb;padding:.5rem}th{background:#eee}.PASS{color:green}"
                + ".FAIL{color:#b00}.SKIP{color:#777}</style></head><body><h1>Execution summary</h1>"
                + "<p>Date/time: " + escape(startedAt.toString()) + " | Duration: " + summary.get("durationMs")
                + " ms</p><p>Total: " + summary.get("total") + " | Passed: " + summary.get("passed")
                + " | Failed: " + summary.get("failed") + " | Skipped: " + summary.get("skipped")
                + " | Pass rate: " + summary.get("passPercentage") + "%</p>"
                + "<p><a href=\"extent-report.html\">Extent report</a> | <a href=\"execution-summary.json\">JSON</a>"
                + " | <a href=\"UI/ui-test-results.html\">UI results</a>"
                + " | <a href=\"API/api-test-results.html\">API results</a>"
                + (Files.exists(reportDirectory.resolve("cucumber-step-report.html"))
                ? " | <a href=\"cucumber-step-report.html\">Cucumber step report</a>" : "")
                + "</p>"
                + resultsTable(RECORDS) + "<script type=\"application/json\" id=\"summary\">"
                + json.replace("</", "<\\/") + "</script></body></html>";
    }

    private static String resultsPage(String type) {
        List<TestExecutionRecord> selected = RECORDS.stream()
                .filter(record -> type.equalsIgnoreCase(record.testType)).toList();
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + type
                + " results</title><style>body{font:15px Arial;margin:2rem}table{border-collapse:collapse}"
                + "td,th{border:1px solid #bbb;padding:.5rem}.PASS{color:green}.FAIL{color:#b00}"
                + ".SKIP{color:#777}</style></head><body><h1>" + type + " test results</h1>"
                + resultsTable(selected) + "<p><a href=\"../index.html\">Dashboard</a></p></body></html>";
    }

    private static String resultsTable(List<TestExecutionRecord> records) {
        StringBuilder html = new StringBuilder("<table><thead><tr><th>Module</th><th>Case ID</th>"
                + "<th>Description</th><th>Type</th><th>Status</th><th>Expected</th><th>Actual / Failure</th>"
                + "<th>Duration (ms)</th></tr></thead><tbody>");
        for (TestExecutionRecord record : records) {
            html.append("<tr><td>").append(escape(record.module)).append("</td><td>")
                    .append(escape(record.testCaseId)).append("</td><td>").append(escape(record.description))
                    .append("</td><td>").append(escape(record.testType)).append("</td><td class=\"")
                    .append(escape(record.status)).append("\">").append(escape(record.status))
                    .append("</td><td>").append(escape(record.expectedResult)).append("</td><td>")
                    .append(escape(record.failureReason == null ? record.actualResult : record.failureReason))
                    .append("</td><td>").append(record.durationMs).append("</td></tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String stackTrace(Throwable throwable) {
        if (throwable == null) return "";
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safeName(String value) {
        return value == null ? "unnamed-case" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
