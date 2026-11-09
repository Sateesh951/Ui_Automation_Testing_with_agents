package com.automation.utilities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValueResolver {
    private static final Pattern ENVIRONMENT_VALUE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private ValueResolver() {}

    public static String resolve(String value) {
        if (value == null) return null;
        Matcher matcher = ENVIRONMENT_VALUE.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String environmentValue = System.getenv(matcher.group(1));
            if (environmentValue == null) {
                throw new IllegalStateException("Required environment variable is not set: " + matcher.group(1));
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(environmentValue));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
