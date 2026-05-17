package com.timetablebot.application.notification;

import java.time.Instant;

public record NotificationTaskPayload(
        String userId,
        String taskId,
        String title,
        Instant deadline,
        String message
) {
}