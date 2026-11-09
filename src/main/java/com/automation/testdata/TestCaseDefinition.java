package com.automation.testdata;

import java.util.List;

public class TestCaseDefinition {
    public String id;
    public String module;
    public String description;
    public String type;
    public String expectedResult;
    public List<String> tags;
    public ApiCase api;
    public UiCase ui;

    @Override
    public String toString() {
        return id + " - " + description;
    }
}
