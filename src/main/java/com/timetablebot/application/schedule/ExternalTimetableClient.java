package com.timetablebot.application.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ExternalTimetableClient {
    private final WebClient webClient;
    private final String telegramBotToken;

    public ExternalTimetableClient(WebClient.Builder webClientBuilder,
                                    @Value("${telegram.bot.token:}") String telegramBotToken) {
        this.webClient = webClientBuilder.build();
        this.telegramBotToken = telegramBotToken;
    }

    public Mono<String> download(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> downloadTelegramFile(String fileId) {
        String getFileUrl = "https://api.telegram.org/bot" + telegramBotToken + "/getFile?file_id=" + fileId;
        return webClient.get()
                .uri(getFileUrl)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .flatMap(json -> {
                    String filePath = json.path("result").path("file_path").asText();
                    if (filePath.isBlank()) {
                        return Mono.error(new IllegalStateException("Не удалось получить путь к файлу от Telegram"));
                    }
                    String downloadUrl = "https://api.telegram.org/file/bot" + telegramBotToken + "/" + filePath;
                    return webClient.get()
                            .uri(downloadUrl)
                            .retrieve()
                            .bodyToMono(String.class);
                });
    }
}
