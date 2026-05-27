package com.timetablebot.infrastructure.task;

import com.timetablebot.domain.user.TaskPriority;
import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.domain.user.TaskType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tasks")
public class TaskDocument {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String title;
    private TaskType type;
    private TaskPriority priority;
    private TaskStatus status;
    private Instant deadline;
    private Instant lastReminderSentAt;          private boolean reminder24hSent = false;     private boolean reminder1hSent  = false;     private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public Instant getLastReminderSentAt() { return lastReminderSentAt; }
    public void setLastReminderSentAt(Instant lastReminderSentAt) { this.lastReminderSentAt = lastReminderSentAt; }
    public boolean isReminder24hSent() { return reminder24hSent; }
    public void setReminder24hSent(boolean reminder24hSent) { this.reminder24hSent = reminder24hSent; }
    public boolean isReminder1hSent() { return reminder1hSent; }
    public void setReminder1hSent(boolean reminder1hSent) { this.reminder1hSent = reminder1hSent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
