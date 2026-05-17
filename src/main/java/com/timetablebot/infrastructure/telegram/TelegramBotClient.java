package com.timetablebot.infrastructure.telegram;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
public class TelegramBotClient {
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    private final TelegramBotProperties properties;
    private final WebClient webClient;

    public TelegramBotClient(TelegramBotProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder.baseUrl("https://api.telegram.org").build();
    }

    public Mono<Void> sendMessage(Long chatId, String text) {
        if (!properties.enabled() || properties.token() == null || properties.token().isBlank() || chatId == null) {
            return Mono.empty();
        }

        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", normalizedText
        );

        return webClient.post()
                .uri("/bot{token}/sendMessage", properties.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(SEND_TIMEOUT)
                .then();
    }
}