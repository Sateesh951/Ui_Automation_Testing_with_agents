package com.automation.tests.cucumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.automation.reporting.ReportManager;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StepReportPlugin implements EventListener {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ConcurrentMap<String, String> SCREENSHOTS_BY_SCENARIO = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScenarioRecord> scenarios = new ConcurrentHashMap<>();

    public static void recordScreenshot(String scenarioName, Path screenshot) {
        String reportPath = "UI/screenshots/" + screenshot.getFileName();
        SCREENSHOTS_BY_SCENARIO.put(scenarioName, reportPath);
        System.setProperty(screenshotProperty(scenarioName), reportPath);
        ReportManager.attachCucumberScreenshot(scenarioName, reportPath);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::onTestStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onTestStepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, event -> writeReports());
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        var testCase = event.getTestCase();
        ScenarioRecord record = new ScenarioRecord();
        record.name = testCase.getName();
        record.feature = testCase.getUri().toString();
        record.line = testCase.getLine();
        record.module = moduleName(testCase.getUri().toString());
        SCREENSHOTS_BY_SCENARIO.remove(record.name);
        System.clearProperty(screenshotProperty(record.name));
        scenarios.put(testCase.getId(), record);
    }

    private void onTestStepStarted(TestStepStarted event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep step)) return;
        ScenarioRecord scenario = scenarios.get(event.getTestCase().getId());
        if (scenario == null) return;

        StepRecord record = new StepRecord();
        record.keyword = step.getStep().getKeyword();
        record.text = step.getStep().getText();
        record.line = step.getStep().getLine();
        record.status = "RUNNING";
        scenario.steps.add(record);
    }

    private void onTestStepFinished(TestStepFinished event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) return;
        ScenarioRecord scenario = scenarios.get(event.getTestCase().getId());
        if (scenario == null || scenario.steps.isEmpty()) return;

        StepRecord record = scenario.steps.get(scenario.steps.size() - 1);
        record.status = event.getResult().getStatus().name();
        record.durationMs = event.getResult().getDuration().toMillis();
        record.error = event.getResult().getError() == null
                ? null : event.getResult().getError().toString();
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        ScenarioRecord scenario = scenarios.get(event.getTestCase().getId());
        if (scenario != null) {
            scenario.status = event.getResult().getStatus().name();
        }
    }

    private void writeReports() {
        String reportPath = System.getProperty("framework.report.dir", "reports");
        Path directory = Path.of(reportPath);
        try {
            Files.createDirectories(directory);
            for (ScenarioRecord scenario : orderedScenarios()) {
                scenario.screenshot = System.getProperty(screenshotProperty(scenario.name),
                        SCREENSHOTS_BY_SCENARIO.get(scenario.name));
                long duration = scenario.steps.stream().mapToLong(step -> step.durationMs).sum();
                String failure = scenario.steps.stream()
                        .filter(step -> step.error != null && !step.error.isBlank())
                        .map(step -> step.error).findFirst().orElse(null);
                ReportManager.recordCucumberScenario(scenario.module,
                        scenario.module.toUpperCase() + "-" + scenario.line,
                        scenario.name, scenario.status, duration, failure, scenario.screenshot);
                System.clearProperty(screenshotProperty(scenario.name));
            }
            MAPPER.writeValue(directory.resolve("cucumber-step-report.json").toFile(),
                    orderedScenarios());
            Files.writeString(directory.resolve("cucumber-step-report.html"),
                    toHtml(orderedScenarios()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the Cucumber step report.", exception);
        }
    }

    private List<ScenarioRecord> orderedScenarios() {
        return scenarios.values().stream()
                .sorted(java.util.Comparator.comparing((ScenarioRecord scenario) -> scenario.feature)
                        .thenComparingInt(scenario -> scenario.line))
                .toList();
    }

    private static String moduleName(String featureUri) {
        String fileName = Path.of(URI.create(featureUri)).getFileName().toString();
        String name = fileName.replaceFirst("\\.feature$", "");
        int separator = name.indexOf(' ');
        return separator > 0 ? name.substring(0, separator) : name;
    }

    private static String screenshotProperty(String scenarioName) {
        return "framework.cucumber.screenshot." + Integer.toUnsignedString(scenarioName.hashCode());
    }

    private static String toHtml(List<ScenarioRecord> report) throws IOException {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html><head><meta charset="utf-8">
                <title>Cucumber step report</title>
                <style>
                body{font:15px Arial,sans-serif;margin:2rem;color:#222}
                table{border-collapse:collapse;width:100%;margin-bottom:2rem}
                th,td{border:1px solid #bbb;padding:.55rem;text-align:left}
                th{background:#eee}.PASSED{color:#08752d;font-weight:bold}
                .FAILED{color:#b00020;font-weight:bold}.SKIPPED,.PENDING,.UNDEFINED{color:#916b00;font-weight:bold}
                </style></head><body><h1>Cucumber step-by-step report</h1>
                <p>Every Gherkin step is listed with its individual execution status.</p>
                """);
        for (ScenarioRecord scenario : report) {
            html.append("<h2>").append(escape(scenario.name)).append("</h2><p>Feature: ")
                    .append(escape(scenario.feature)).append(" | Scenario status: <strong>")
                    .append(escape(scenario.status)).append("</strong></p>")
                    .append("<table><thead><tr><th>#</th><th>Keyword</th><th>Step</th>")
                    .append("<th>Status</th><th>Duration (ms)</th><th>Failure</th></tr></thead><tbody>");
            int index = 1;
            for (StepRecord step : scenario.steps) {
                html.append("<tr><td>").append(index++).append("</td><td>")
                        .append(escape(step.keyword)).append("</td><td>")
                        .append(escape(step.text)).append("</td><td class=\"")
                        .append(escape(step.status)).append("\">").append(escape(step.status))
                        .append("</td><td>").append(step.durationMs).append("</td><td>")
                        .append(escape(step.error)).append("</td></tr>");
            }
            html.append("</tbody></table>");
            if (scenario.screenshot != null) {
                html.append("<p><a href=\"").append(escape(scenario.screenshot))
                        .append("\">View failure screenshot</a></p>");
            }
        }
        return html.append("</body></html>").toString();
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static class ScenarioRecord {
        public String feature;
        public String module;
        public String name;
        public String screenshot;
        public int line;
        public String status;
        public final List<StepRecord> steps = new ArrayList<>();
    }

    public static class StepRecord {
        public String keyword;
        public String text;
        public int line;
        public String status;
        public long durationMs;
        public String error;
    }
}
