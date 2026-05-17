package com.timetablebot.infrastructure.notification;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationQueueConfigTest {

    private final NotificationQueueConfig config = new NotificationQueueConfig();

    @Test
    void shouldCreateMainQueueWithDlqArguments() {
        Queue queue = config.notificationsQueue("notifications.queue", "notifications.dlx", "notifications.dead");

        assertEquals("notifications.queue", queue.getName());
        assertEquals("notifications.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("notifications.dead", queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void shouldCreateDlxAndDlqBinding() {
        DirectExchange dlx = config.notificationsDlx("notifications.dlx");
        Queue dlq = config.notificationsDeadLetterQueue("notifications.queue.dlq");
        Binding binding = config.notificationsDlqBinding(dlq, dlx, "notifications.dead");

        assertNotNull(binding);
        assertEquals("notifications.queue.dlq", binding.getDestination());
        assertEquals("notifications.dlx", binding.getExchange());
        assertEquals("notifications.dead", binding.getRoutingKey());
    }
}