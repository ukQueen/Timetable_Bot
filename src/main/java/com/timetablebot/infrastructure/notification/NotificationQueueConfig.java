package com.timetablebot.infrastructure.notification;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationQueueConfig {
    @Bean
    public Queue notificationsQueue() {
        return new Queue("notifications.queue", true);
    }
}