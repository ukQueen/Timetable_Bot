package com.timetablebot.api.telegram;

import com.timetablebot.application.telegram.MessageHandlerModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/telegram", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelegramWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final MessageHandlerModule messageHandlerModule;
    private final TelegramBotClient telegramBotClient;

    public TelegramWebhookController(MessageHandlerModule messageHandlerModule, TelegramBotClient telegramBotClient) {
        this.messageHandlerModule = messageHandlerModule;
        this.telegramBotClient = telegramBotClient;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<BotMessageResponse> handleMessage(@RequestBody TelegramUpdateRequest update) {
        Long chatId = update != null && update.message() != null && update.message().chat() != null
                ? update.message().chat().id()
                : null;

        return messageHandlerModule.handle(update)
                .flatMap(response -> telegramBotClient.sendMessage(chatId, response.message())
                        .doOnError(ex -> log.warn("Failed to send Telegram message to chatId={}", chatId, ex))
                        .onErrorResume(ex -> Mono.empty())
                        .thenReturn(response));
    }
}