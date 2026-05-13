package com.timetablebot.infrastructure.user;

import com.timetablebot.domain.user.OnboardingStatus;
import com.timetablebot.domain.user.WeekStartPreference;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class UserDocument {
    @Id
    private String id;
    private String timezone;
    private WeekStartPreference weekStartPreference;
    private OnboardingStatus onboardingStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public WeekStartPreference getWeekStartPreference() { return weekStartPreference; }
    public void setWeekStartPreference(WeekStartPreference weekStartPreference) { this.weekStartPreference = weekStartPreference; }
    public OnboardingStatus getOnboardingStatus() { return onboardingStatus; }
    public void setOnboardingStatus(OnboardingStatus onboardingStatus) { this.onboardingStatus = onboardingStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
