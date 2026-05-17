package com.timetablebot.infrastructure.notification;

import com.timetablebot.application.notification.NotificationTaskPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public NotificationPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${notifications.queue:notifications.queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publishTask(NotificationTaskPayload payload) {
        rabbitTemplate.convertAndSend("", queueName, payload);
    }
}