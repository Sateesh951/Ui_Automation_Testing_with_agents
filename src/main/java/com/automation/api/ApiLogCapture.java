package com.automation.api;

public final class ApiLogCapture {
    private static final ThreadLocal<StringBuilder> LOG = ThreadLocal.withInitial(StringBuilder::new);

    private ApiLogCapture() {}

    public static void add(String entry) {
        LOG.get().append(entry).append(System.lineSeparator());
    }

    public static String get() {
        return LOG.get().toString();
    }

    public static void clear() {
        LOG.remove();
    }
}
