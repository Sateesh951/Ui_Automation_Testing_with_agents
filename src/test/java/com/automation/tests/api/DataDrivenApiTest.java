package com.automation.tests.api;

import com.automation.api.ApiClient;
import com.automation.api.ApiLogCapture;
import com.automation.config.FrameworkConfig;
import com.automation.testdata.ApiAssertion;
import com.automation.testdata.ApiCase;
import com.automation.testdata.TestCaseDefinition;
import com.automation.testdata.TestCaseStore;
import com.automation.utilities.ValueResolver;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataDrivenApiTest {
    @DataProvider(name = "apiCases")
    public static Object[][] apiCases() {
        return TestCaseStore.casesFor("API");
    }

    @Test(dataProvider = "apiCases", groups = {"api"})
    public void executeApiCase(TestCaseDefinition testCase) {
        ApiLogCapture.clear();
        ApiCase api = testCase.api;
        if (api == null || api.method == null || api.path == null || api.expectedStatus == null) {
            throw new SkipException("API method, path and expectedStatus are required.");
        }
        if (FrameworkConfig.get("api.base.url", "").isBlank()) {
            throw new SkipException("Set api.base.url (or API_BASE_URL) to run API cases.");
        }

        try {
            Map<String, String> headers = resolveMap(api.headers);
            Map<String, String> queryParams = resolveMap(api.queryParams);
            Map<String, String> pathParams = resolveMap(api.pathParams);
            Object body = resolveBody(api.body);
            Response response = new ApiClient().send(api.method, api.path, headers, queryParams, pathParams,
                    body, api.contentType, api.accept);
            SoftAssert assertions = new SoftAssert();
            assertions.assertEquals(response.statusCode(), api.expectedStatus.intValue(), "HTTP status mismatch");
            long maxResponseTime = api.maxResponseMs == null
                    ? FrameworkConfig.getInt("api.max.response.ms", 5000) : api.maxResponseMs;
            assertions.assertTrue(response.time() <= maxResponseTime,
                    "Response time " + response.time() + "ms exceeded " + maxResponseTime + "ms");
            if (api.expectedHeaders != null) {
                api.expectedHeaders.forEach((name, value) -> assertions.assertEquals(
                        response.getHeader(name), ValueResolver.resolve(value),
                        "Response header mismatch: " + name));
            }
            if (api.assertions != null) {
                for (ApiAssertion assertion : api.assertions) {
                    assertions.assertEquals(response.jsonPath().getString(assertion.jsonPath),
                            ValueResolver.resolve(assertion.equals),
                            "Response mismatch at JSONPath: " + assertion.jsonPath);
                }
            }
            if (api.responseSchema != null && !api.responseSchema.isBlank()) {
                assertions.assertTrue(JsonSchemaValidator.matchesJsonSchemaInClasspath(api.responseSchema)
                                .matches(response.asString()),
                        "Response does not match schema: " + api.responseSchema);
            }
            assertions.assertAll();
        } catch (RuntimeException | AssertionError failure) {
            org.testng.Reporter.getCurrentTestResult().setAttribute("api.log", ApiLogCapture.get());
            throw failure;
        }
    }

    private static Map<String, String> resolveMap(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> result.put(key, ValueResolver.resolve(value)));
        return result;
    }

    private static Object resolveBody(Object body) {
        if (body instanceof String text) return ValueResolver.resolve(text);
        if (body instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, value) -> resolved.put(String.valueOf(key), resolveBody(value)));
            return resolved;
        }
        if (body instanceof Iterable<?> values) {
            java.util.List<Object> resolved = new java.util.ArrayList<>();
            values.forEach(value -> resolved.add(resolveBody(value)));
            return resolved;
        }
        return body;
    }
}
