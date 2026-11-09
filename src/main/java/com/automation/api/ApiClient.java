package com.automation.api;

import com.automation.config.FrameworkConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

public class ApiClient {
    private final String baseUrl;

    public ApiClient() {
        this(FrameworkConfig.get("api.base.url", ""));
    }

    public ApiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Set api.base.url (or API_BASE_URL) before running API cases.");
        }
        this.baseUrl = baseUrl;
    }

    public Response send(String method, String path, Map<String, ?> headers,
                         Map<String, ?> queryParams, Map<String, ?> pathParams, Object body) {
        return send(method, path, headers, queryParams, pathParams, body,
                "application/json", "application/json");
    }

    public Response send(String method, String path, Map<String, ?> headers,
                         Map<String, ?> queryParams, Map<String, ?> pathParams, Object body,
                         String contentType, String accept) {
        RequestSpecification request = RestAssured.given()
                .baseUri(baseUrl)
                .filter(new ApiLoggingFilter())
                .headers(headers == null ? Map.of() : headers)
                .queryParams(queryParams == null ? Map.of() : queryParams)
                .pathParams(pathParams == null ? Map.of() : pathParams)
                .accept(accept == null ? "application/json" : accept);
        if (body != null) request.contentType(contentType == null ? "application/json" : contentType).body(body);
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        return switch (normalizedMethod) {
            case "GET" -> request.get(path);
            case "POST" -> request.post(path);
            case "PUT" -> request.put(path);
            case "PATCH" -> request.patch(path);
            case "DELETE" -> request.delete(path);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }
}
