package com.timetablebot.domain.schedule;

import java.time.Instant;

public record ScheduleEvent(
        String id,
        String userId,
        EventType type,
        String title,
        String place,
        String source,
        Instant startsAt,
        Instant endsAt
) {
}
