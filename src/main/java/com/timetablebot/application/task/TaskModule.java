package com.timetablebot.application.task;

import com.timetablebot.domain.user.*;
import com.timetablebot.application.notification.NotificationTaskPayload;
import com.timetablebot.infrastructure.notification.NotificationPublisher;
import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;

@Component
public class TaskModule {
    private final TaskRepository taskRepository;
    private final NotificationPublisher notificationPublisher;

    public TaskModule(TaskRepository taskRepository, NotificationPublisher notificationPublisher) {
        this.taskRepository = taskRepository;
        this.notificationPublisher = notificationPublisher;
    }

    public Mono<TaskItem> createTask(String userId, String title, Instant deadline, TaskPriority priority, TaskType type) {
        TaskDocument doc = new TaskDocument();
        doc.setUserId(userId);
        doc.setTitle(title);
        doc.setDeadline(deadline);
        doc.setPriority(priority);
        doc.setType(type);
        doc.setStatus(TaskStatus.OPEN);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return taskRepository.save(doc)
                .doOnNext(saved -> notificationPublisher.publishTask(new NotificationTaskPayload(
                        saved.getUserId(),
                        saved.getId(),
                        saved.getTitle(),
                        saved.getDeadline(),
                        "Напоминание: задача \"" + saved.getTitle() + "\" до " + saved.getDeadline()
                )))
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksForToday(String userId, ZoneId zoneId) {
        LocalDate now = LocalDate.now(zoneId);
        return tasksByRange(userId, now, now.plusDays(1), zoneId);
    }

    public Flux<TaskItem> tasksForWeek(String userId, ZoneId zoneId) {
        LocalDate now = LocalDate.now(zoneId);
        return tasksByRange(userId, now, now.plusDays(7), zoneId);
    }


    public Mono<TaskItem> updateTask(String userId, String taskId, String title, Instant deadline, TaskPriority priority, TaskType type) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Задача не найдена.")))
                .flatMap(doc -> {
                    doc.setTitle(title);
                    doc.setDeadline(deadline);
                    doc.setPriority(priority);
                    doc.setType(type);
                    doc.setUpdatedAt(Instant.now());
                    return taskRepository.save(doc);
                })
                .map(this::toDomain);
    }

    public Flux<TaskItem> overdueTasks(String userId, ZoneId zoneId) {
        Instant now = ZonedDateTime.now(zoneId).toInstant();
        return taskRepository.findAllByUserIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(userId, TaskStatus.OPEN, now)
                .map(this::toDomain);
    }

    public Mono<TaskItem> markDone(String userId, String taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Задача не найдена.")))
                .flatMap(doc -> {
                    doc.setStatus(TaskStatus.DONE);
                    doc.setUpdatedAt(Instant.now());
                    return taskRepository.save(doc);
                }).map(this::toDomain);
    }

    public Mono<Void> deleteTask(String userId, String taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Задача не найдена.")))
                .flatMap(doc -> taskRepository.deleteByIdAndUserId(taskId, userId));
    }

    private Flux<TaskItem> tasksByRange(String userId, LocalDate from, LocalDate to, ZoneId zoneId) {
        return taskRepository.findAllByUserIdAndDeadlineBetweenOrderByDeadlineAsc(userId, from.atStartOfDay(zoneId).toInstant(), to.atStartOfDay(zoneId).toInstant())
                .map(this::toDomain);
    }

    private TaskItem toDomain(TaskDocument doc) {
        return new TaskItem(doc.getId(), doc.getUserId(), doc.getTitle(), doc.getType(), doc.getPriority(), doc.getStatus(), doc.getDeadline(), doc.getCreatedAt(), doc.getUpdatedAt());
    }
}