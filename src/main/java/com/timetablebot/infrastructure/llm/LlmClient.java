package com.timetablebot.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(30);
    private static final String MODEL = "GigaChat";
    private static final String SCOPE = "GIGACHAT_API_PERS";

    private final WebClient httpClient;
    private final String credentials;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public LlmClient(
            ObjectMapper objectMapper,
            @Value("${llm.api.key:}") String apiKey,
            @Value("${llm.api.enabled:false}") boolean enabled) {
        this.credentials = apiKey;
        this.enabled = enabled;
        this.objectMapper = objectMapper;

        // Отключаем проверку SSL — GigaChat использует российские сертификаты
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            HttpClient nettyClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));
            this.httpClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(nettyClient))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-disabled WebClient", e);
        }
    }

    public boolean isEnabled() {
        return enabled && credentials != null && !credentials.isBlank();
    }

    public Mono<String> ask(String systemPrompt, String userMessage) {
        if (!isEnabled()) {
            return Mono.just("LLM-советник недоступен. Настройте переменную окружения LLM_API_KEY.");
        }

        return getToken()
                .flatMap(token -> sendRequest(token, systemPrompt, userMessage))
                .onErrorResume(ex -> {
                    log.warn("GigaChat API request failed: {}", ex.getMessage());
                    return Mono.just("Советник временно недоступен. Попробуйте позже.");
                });
    }

    private Mono<String> getToken() {
        if (cachedToken.get() != null && Instant.now().isBefore(tokenExpiresAt)) {
            return Mono.just(cachedToken.get());
        }
        return fetchNewToken();
    }

    private Mono<String> fetchNewToken() {
        return httpClient.post()
                .uri("https://ngw.devices.sberbank.ru:9443/api/v2/oauth")
                .header("Authorization", "Basic " + credentials)
                .header("RqUID", UUID.randomUUID().toString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("scope=" + SCOPE)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(LLM_TIMEOUT)
                .map(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        String token = root.path("access_token").asText();
                        long expiresAt = root.path("expires_at").asLong();
                        cachedToken.set(token);
                        tokenExpiresAt = Instant.ofEpochMilli(expiresAt).minusSeconds(60);
                        log.info("GigaChat token obtained successfully");
                        return token;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse GigaChat token: " + e.getMessage());
                    }
                })
                .doOnError(ex -> log.warn("GigaChat token request failed: {}", ex.getMessage()));
    }

    private Mono<String> sendRequest(String token, String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", messages,
                "max_tokens", 1024,
                "temperature", 0.7
        );

        return httpClient.post()
                .uri("https://gigachat.devices.sberbank.ru/api/v1/chat/completions")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(LLM_TIMEOUT)
                .map(this::extractText);
    }

    private String extractText(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("choices").get(0)
                    .path("message").path("content")
                    .asText("Нет ответа от советника.");
        } catch (Exception ex) {
            log.warn("Failed to parse GigaChat response: {}", ex.getMessage());
            return "Ошибка разбора ответа советника.";
        }
    }
}
