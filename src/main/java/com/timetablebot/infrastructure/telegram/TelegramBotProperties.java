package com.timetablebot.infrastructure.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record TelegramBotProperties(
        @Value("${telegram.bot.token:}") String token,
        @Value("${telegram.bot.enabled:false}") boolean enabled,
        @Value("${telegram.bot.webhook-secret:}") String webhookSecret,
        @Value("${telegram.bot.webhook-url:}") String webhookUrl,
        @Value("${telegram.bot.register-webhook-on-startup:false}") boolean registerWebhookOnStartup
) {
}
