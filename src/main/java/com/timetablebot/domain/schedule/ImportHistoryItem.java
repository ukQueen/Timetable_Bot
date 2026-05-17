package com.timetablebot.domain.schedule;

import java.time.Instant;

public record ImportHistoryItem(
        String source,
        ImportStatus status,
        Integer importedCount,
        String errorMessage,
        Instant createdAt
) {
}
