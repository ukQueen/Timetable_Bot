package com.timetablebot.application.notification;

import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationModule {
    private final TelegramBotClient telegramBotClient;

    public NotificationModule(TelegramBotClient telegramBotClient) {
        this.telegramBotClient = telegramBotClient;
    }

    @RabbitListener(queues = "${notifications.queue:notifications.queue}")
    public void onTaskNotification(NotificationTaskPayload payload) {
        Long chatId = Long.parseLong(payload.userId());
        telegramBotClient.sendMessage(chatId, payload.message()).subscribe();
    }
}