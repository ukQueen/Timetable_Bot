package com.timetablebot.infrastructure.notification;

import com.timetablebot.application.notification.NotificationTaskPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldPublishToConfiguredQueue() {
        NotificationPublisher publisher = new NotificationPublisher(rabbitTemplate, "notifications.custom.queue");
        NotificationTaskPayload payload = new NotificationTaskPayload("1001", "t1", "Task", Instant.now(), "message");

        publisher.publishTask(payload);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend("", "notifications.custom.queue", payloadCaptor.capture());
        assertEquals(payload, payloadCaptor.getValue());
    }
}