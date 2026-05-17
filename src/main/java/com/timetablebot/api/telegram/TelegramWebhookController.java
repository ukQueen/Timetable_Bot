package com.timetablebot.api.telegram;

import com.timetablebot.application.telegram.MessageHandlerModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import com.timetablebot.infrastructure.telegram.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/telegram", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelegramWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final MessageHandlerModule messageHandlerModule;
    private final TelegramBotClient telegramBotClient;
    private final TelegramBotProperties telegramBotProperties;

    public TelegramWebhookController(MessageHandlerModule messageHandlerModule,
                                     TelegramBotClient telegramBotClient,
                                     TelegramBotProperties telegramBotProperties) {
        this.messageHandlerModule = messageHandlerModule;
        this.telegramBotClient = telegramBotClient;
        this.telegramBotProperties = telegramBotProperties;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BotMessageResponse>> handleMessage(
            @RequestBody TelegramUpdateRequest update,
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretHeader) {

        if (isSecretInvalid(secretHeader)) {
            return Mono.just(ResponseEntity.status(401)
                    .body(BotMessageResponse.error("Unauthorized webhook request.")));
        }

        Long chatId = update != null && update.message() != null && update.message().chat() != null
                ? update.message().chat().id()
                : null;

        return messageHandlerModule.handle(update)
                .flatMap(response -> telegramBotClient.sendMessage(chatId, response.message())
                        .doOnError(ex -> log.warn("Failed to send Telegram message to chatId={}", chatId, ex))
                        .onErrorResume(ex -> Mono.empty())
                        .thenReturn(ResponseEntity.ok(response)));
    }

    private boolean isSecretInvalid(String secretHeader) {
        String expectedSecret = telegramBotProperties.webhookSecret();
        if (!StringUtils.hasText(expectedSecret)) {
            return false;
        }
        return !expectedSecret.equals(secretHeader);
    }
}