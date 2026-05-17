package com.timetablebot.application.notification;

import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationModule {
    private static final Logger log = LoggerFactory.getLogger(NotificationModule.class);

    private final TelegramBotClient telegramBotClient;

    public NotificationModule(TelegramBotClient telegramBotClient) {
        this.telegramBotClient = telegramBotClient;
    }

    @RabbitListener(queues = "${notifications.queue:notifications.queue}")
    public void onTaskNotification(NotificationTaskPayload payload) {
        Long chatId;
        try {
            chatId = Long.parseLong(payload.userId());
        } catch (NumberFormatException ex) {
            log.warn("Invalid userId for notification payload: {}", payload.userId());
            throw new AmqpRejectAndDontRequeueException("Invalid userId in notification payload", ex);
        }

        try {
            telegramBotClient.sendMessage(chatId, payload.message()).block();
        } catch (Exception ex) {
            log.warn("Failed to send task notification to chatId={}", chatId, ex);
            throw new RuntimeException("Notification delivery failed", ex);
        }
    }
}