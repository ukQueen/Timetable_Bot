package com.timetablebot.infrastructure.schedule;

import com.timetablebot.domain.schedule.EventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "schedule_events")
public class ScheduleEventDocument {
    @Id
    private String id;
    @Indexed
    private String userId;
    private EventType type;
    private String title;
    private String place;
    private String source;
    private Instant startsAt;
    private Instant endsAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
}
