package com.timetablebot.infrastructure.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(String token, boolean enabled) {
}