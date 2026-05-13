package com.timetablebot.api.telegram;

import com.timetablebot.application.telegram.MessageHandlerModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/telegram", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelegramWebhookController {
    private final MessageHandlerModule messageHandlerModule;

    public TelegramWebhookController(MessageHandlerModule messageHandlerModule) {
        this.messageHandlerModule = messageHandlerModule;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<BotMessageResponse> handleMessage(@RequestBody TelegramUpdateRequest update) {
        return messageHandlerModule.handle(update);
    }
}
