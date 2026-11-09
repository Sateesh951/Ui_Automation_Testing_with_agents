package com.automation.testdata;

import java.util.List;
import java.util.Map;

public class ApiCase {
    public String method;
    public String path;
    public String contentType;
    public String accept;
    public Integer expectedStatus;
    public Map<String, String> expectedHeaders;
    public Map<String, String> headers;
    public Map<String, String> queryParams;
    public Map<String, String> pathParams;
    public Object body;
    public Long maxResponseMs;
    public String responseSchema;
    public List<ApiAssertion> assertions;
}
