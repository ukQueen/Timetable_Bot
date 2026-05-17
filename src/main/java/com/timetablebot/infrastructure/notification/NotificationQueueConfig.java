package com.timetablebot.infrastructure.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class NotificationQueueConfig {
    @Bean
    public Queue notificationsQueue(@Value("${notifications.queue:notifications.queue}") String queueName,
                                    @Value("${notifications.dlq.exchange:notifications.dlx}") String dlx,
                                    @Value("${notifications.dlq.routing-key:notifications.dead}") String dlRoutingKey) {
        return new Queue(queueName, true, false, false, Map.of(
                "x-dead-letter-exchange", dlx,
                "x-dead-letter-routing-key", dlRoutingKey
        ));
    }

    @Bean
    public DirectExchange notificationsDlx(@Value("${notifications.dlq.exchange:notifications.dlx}") String dlx) {
        return new DirectExchange(dlx, true, false);
    }

    @Bean
    public Queue notificationsDeadLetterQueue(@Value("${notifications.dlq.queue:notifications.queue.dlq}") String dlqName) {
        return new Queue(dlqName, true);
    }

    @Bean
    public Binding notificationsDlqBinding(Queue notificationsDeadLetterQueue,
                                           DirectExchange notificationsDlx,
                                           @Value("${notifications.dlq.routing-key:notifications.dead}") String dlRoutingKey) {
        return BindingBuilder.bind(notificationsDeadLetterQueue)
                .to(notificationsDlx)
                .with(dlRoutingKey);
    }
}