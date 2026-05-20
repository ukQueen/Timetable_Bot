package com.timetablebot.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DotenvPropertyLoader {
    private DotenvPropertyLoader() {
    }

    public static void loadFromProjectRoot() {
        Path dotenvPath = Path.of(".env");
        if (!Files.exists(dotenvPath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(dotenvPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();

                if (key.isEmpty() || System.getProperty(key) != null) {
                    continue;
                }
                System.setProperty(key, unquote(value));
            }
        } catch (IOException ignored) {
            // Keep startup resilient; env vars from OS still work.
        }
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
