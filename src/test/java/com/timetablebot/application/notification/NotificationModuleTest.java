package com.timetablebot.application.notification;

import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationModuleTest {

    @Mock
    private TelegramBotClient telegramBotClient;

    @InjectMocks
    private NotificationModule notificationModule;

    @Test
    void shouldRejectInvalidUserIdWithoutRequeue() {
        NotificationTaskPayload payload = new NotificationTaskPayload(
                "not-a-number",
                "t1",
                "Task",
                Instant.now(),
                "msg"
        );

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> notificationModule.onTaskNotification(payload));
    }

    @Test
    void shouldPropagateSendFailureForRetryDlqPolicy() {
        NotificationTaskPayload payload = new NotificationTaskPayload(
                "1001",
                "t1",
                "Task",
                Instant.now(),
                "msg"
        );

        when(telegramBotClient.sendMessage(anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("telegram down")));

        assertThrows(RuntimeException.class,
                () -> notificationModule.onTaskNotification(payload));
    }

    @Test
    void shouldSendNotificationSuccessfully() {
        NotificationTaskPayload payload = new NotificationTaskPayload(
                "1001",
                "t1",
                "Task",
                Instant.now(),
                "msg"
        );

        when(telegramBotClient.sendMessage(anyLong(), anyString())).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> notificationModule.onTaskNotification(payload));
        verify(telegramBotClient).sendMessage(1001L, "msg");
    }
}