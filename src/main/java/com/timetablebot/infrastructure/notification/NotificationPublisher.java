package com.timetablebot.infrastructure.notification;

import com.timetablebot.application.notification.NotificationTaskPayload;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationPublisher {
    private final RabbitTemplate rabbitTemplate;

    public NotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTask(NotificationTaskPayload payload) {
        rabbitTemplate.convertAndSend("", "notifications.queue", payload);
    }
}