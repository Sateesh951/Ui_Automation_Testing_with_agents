package com.automation.testdata;

import com.automation.config.FrameworkConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TestCaseStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestCaseStore() {}

    public static Object[][] casesFor(String type) {
        String file = FrameworkConfig.get("testcases.file", "testdata/test-cases.json");
        Path path = Path.of(file);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Test case file does not exist: " + path.toAbsolutePath());
        }
        try {
            List<TestCaseDefinition> definitions = MAPPER.readValue(path.toFile(),
                    new TypeReference<>() {});
            String selectedId = System.getProperty("test.caseId");
            String selectedModule = System.getProperty("test.module");
            String[] selectedTags = System.getProperty("test.tags", "").split(",");
            List<TestCaseDefinition> selected = definitions.stream()
                    .filter(test -> type.equalsIgnoreCase(test.type))
                    .filter(test -> selectedId == null || selectedId.equalsIgnoreCase(test.id))
                    .filter(test -> selectedModule == null || selectedModule.equalsIgnoreCase(test.module))
                    .filter(test -> selectedTags.length == 0 || selectedTags[0].isBlank()
                            || Arrays.stream(selectedTags).map(String::trim).anyMatch(tag ->
                            test.tags != null && test.tags.stream().anyMatch(value ->
                                    value.toLowerCase(Locale.ROOT).equals(tag.toLowerCase(Locale.ROOT)))))
                    .toList();
            return selected.stream().map(test -> new Object[]{test}).toArray(Object[][]::new);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse test case file: " + path.toAbsolutePath(), exception);
        }
    }
}
