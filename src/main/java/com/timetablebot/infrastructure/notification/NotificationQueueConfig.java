package com.timetablebot.infrastructure.notification;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationQueueConfig {
    @Bean
    public Queue notificationsQueue(@Value("${notifications.queue:notifications.queue}") String queueName) {
        return new Queue(queueName, true);
    }
}