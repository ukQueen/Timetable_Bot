package com.timetablebot.domain.user;

import java.time.Instant;

public record TaskItem(
        String id,
        String userId,
        String title,
        TaskType type,
        TaskPriority priority,
        TaskStatus status,
        Instant deadline,
        Instant createdAt,
        Instant updatedAt
) {
}