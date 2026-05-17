package com.timetablebot.infrastructure.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TelegramWebhookRegistrar implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramBotClient telegramBotClient;
    private final TelegramBotProperties properties;

    public TelegramWebhookRegistrar(TelegramBotClient telegramBotClient, TelegramBotProperties properties) {
        this.telegramBotClient = telegramBotClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !properties.registerWebhookOnStartup()) {
            return;
        }
        if (!StringUtils.hasText(properties.webhookUrl())) {
            log.warn("telegram.bot.register-webhook-on-startup is true, but webhook URL is empty");
            return;
        }

        telegramBotClient.registerWebhook(properties.webhookUrl(), properties.webhookSecret())
                .doOnSuccess(unused -> log.info("Telegram webhook registration request sent for URL={}", properties.webhookUrl()))
                .doOnError(ex -> log.warn("Failed to register Telegram webhook", ex))
                .onErrorResume(ex -> reactor.core.publisher.Mono.empty())
                .block();
    }
}