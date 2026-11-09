package com.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class FrameworkConfig {
    private static final Properties PROPERTIES = load();

    private FrameworkConfig() {}

    private static Properties load() {
        Properties properties = new Properties();
        String configuredPath = System.getProperty("framework.config");
        Path path = configuredPath == null
                ? Path.of("src", "test", "resources", "config.properties")
                : Path.of(configuredPath);
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read framework config: " + path, exception);
            }
        }
        return properties;
    }

    public static String get(String key, String defaultValue) {
        String systemValue = System.getProperty("framework." + key);
        if (systemValue != null) return systemValue;
        String envValue = System.getenv(key.replace('.', '_').toUpperCase(Locale.ROOT));
        if (envValue != null) return envValue;
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, Integer.toString(defaultValue)));
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, Boolean.toString(defaultValue)));
    }
}
