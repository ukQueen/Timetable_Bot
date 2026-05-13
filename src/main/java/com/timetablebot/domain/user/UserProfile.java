package com.timetablebot.domain.user;

import java.time.Instant;

public record UserProfile(
        String userId,
        String timezone,
        WeekStartPreference weekStartPreference,
        OnboardingStatus onboardingStatus,
        Instant createdAt,
        Instant updatedAt,
        boolean isNewUser
) {
}
