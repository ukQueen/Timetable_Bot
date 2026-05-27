package com.timetablebot.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DotenvPropertyLoader {
    private DotenvPropertyLoader() {
    }

    public static void loadFromProjectRoot() {
                Path dotenvPath = Path.of(".env");
        if (Files.exists(dotenvPath)) {
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
                            }
        }

                mapEnvToProperty("MONGODB_URI", "MONGODB_URI");
        mapEnvToProperty("RABBITMQ_HOST", "RABBITMQ_HOST");
        mapEnvToProperty("RABBITMQ_PORT", "RABBITMQ_PORT");
        mapEnvToProperty("RABBITMQ_USERNAME", "RABBITMQ_USERNAME");
        mapEnvToProperty("RABBITMQ_PASSWORD", "RABBITMQ_PASSWORD");
        mapEnvToProperty("SERVER_PORT", "SERVER_PORT");
        mapEnvToProperty("HEALTHCHECK_AUTHORS", "HEALTHCHECK_AUTHORS");

        mapEnvToProperty("TELEGRAM_BOT_TOKEN", "telegram.bot.token");
        mapEnvToProperty("TELEGRAM_BOT_ENABLED", "telegram.bot.enabled");
        mapEnvToProperty("TELEGRAM_BOT_WEBHOOK_URL", "telegram.bot.webhook-url");
        mapEnvToProperty("TELEGRAM_BOT_WEBHOOK_SECRET", "telegram.bot.webhook-secret");
        mapEnvToProperty("TELEGRAM_BOT_REGISTER_WEBHOOK_ON_STARTUP", "telegram.bot.register-webhook-on-startup");

        mapEnvToProperty("ADMIN_AUTH_USERNAME", "admin.auth.username");
        mapEnvToProperty("ADMIN_AUTH_PASSWORD", "admin.auth.password");
        mapEnvToProperty("ADMIN_AUTH_TOKEN", "admin.auth.token");

        mapEnvToProperty("NOTIFICATIONS_QUEUE", "notifications.queue");
        mapEnvToProperty("NOTIFICATIONS_TASK_REMINDER_LEAD_MINUTES", "notifications.task-reminder-lead-minutes");
        mapEnvToProperty("NOTIFICATIONS_SCHEDULER_FIXED_DELAY_MS", "notifications.scheduler-fixed-delay-ms");
        mapEnvToProperty("NOTIFICATIONS_DLQ_EXCHANGE", "notifications.dlq.exchange");
        mapEnvToProperty("NOTIFICATIONS_DLQ_QUEUE", "notifications.dlq.queue");
        mapEnvToProperty("NOTIFICATIONS_DLQ_ROUTING_KEY", "notifications.dlq.routing-key");

        mapEnvToProperty("LLM_API_KEY", "llm.api.key");
        mapEnvToProperty("LLM_API_ENABLED", "llm.api.enabled");
    }

    private static void mapEnvToProperty(String envVar, String propertyName) {
        if (System.getProperty(propertyName) != null) {
            return;         }
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            System.setProperty(propertyName, value);
        }
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
