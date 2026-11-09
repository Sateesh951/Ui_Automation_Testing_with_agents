package com.automation.api;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.regex.Pattern;

public class ApiLoggingFilter implements Filter {
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\"(?:password|token|secret|authorization|access_token|refresh_token)\"\\s*:\\s*\")[^\"]*(\")");

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpecification,
                           FilterContext context) {
        String requestBody = sanitize(String.valueOf(request.getBody()));
        String requestHeaders = request.getHeaders().asList().stream()
                .filter(header -> !header.getName().equalsIgnoreCase("authorization")
                        && !header.getName().equalsIgnoreCase("cookie"))
                .map(header -> header.getName() + ": " + sanitize(header.getValue()))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        ApiLogCapture.add("REQUEST " + request.getMethod() + " " + request.getURI()
                + System.lineSeparator() + requestHeaders + requestBody);
        try {
            Response response = context.next(request, responseSpecification);
            ApiLogCapture.add("RESPONSE " + response.statusCode() + System.lineSeparator()
                    + sanitize(response.getBody().asString()));
            return response;
        } catch (RuntimeException exception) {
            ApiLogCapture.add("TRANSPORT FAILURE " + exception);
            throw exception;
        }
    }

    private static String sanitize(String value) {
        return SENSITIVE_JSON.matcher(value == null ? "" : value).replaceAll("$1[REDACTED]$2");
    }
}
