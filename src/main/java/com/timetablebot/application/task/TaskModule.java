package com.timetablebot.application.task;

import com.timetablebot.domain.user.*;
import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;

@Component
public class TaskModule {
    private final TaskRepository taskRepository;

    public TaskModule(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
        return taskRepository.save(doc).map(this::toDomain);
    }

    public Flux<TaskItem> allOpenTasks(String userId) {
        return taskRepository.findAllByUserIdAndStatusOrderByDeadlineAsc(userId, TaskStatus.OPEN)
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksForToday(String userId, ZoneId zoneId) {
        Instant now = Instant.now();
        Instant endOfDay = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
        return taskRepository.findAllByUserIdAndStatusAndDeadlineBetweenOrderByDeadlineAsc(
                userId, TaskStatus.OPEN, now, endOfDay)
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksForTomorrow(String userId, ZoneId zoneId) {
        Instant startOfTomorrow = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant endOfTomorrow   = LocalDate.now(zoneId).plusDays(2).atStartOfDay(zoneId).toInstant();
        return taskRepository.findAllByUserIdAndStatusAndDeadlineBetweenOrderByDeadlineAsc(
                userId, TaskStatus.OPEN, startOfTomorrow, endOfTomorrow)
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksForWeek(String userId, ZoneId zoneId) {
        Instant now = Instant.now();
        Instant endOfWeek = LocalDate.now(zoneId).plusDays(7).atStartOfDay(zoneId).toInstant();
        return taskRepository.findAllByUserIdAndStatusAndDeadlineBetweenOrderByDeadlineAsc(
                userId, TaskStatus.OPEN, now, endOfWeek)
                .map(this::toDomain);
    }

    public Flux<TaskItem> overdueTasks(String userId, ZoneId zoneId) {
        Instant now = Instant.now();
        return taskRepository.findAllByUserIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(
                userId, TaskStatus.OPEN, now)
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksByPriority(String userId, TaskPriority priority) {
        return taskRepository.findAllByUserIdAndPriorityAndStatusOrderByDeadlineAsc(userId, priority, TaskStatus.OPEN)
                .map(this::toDomain);
    }

    public Flux<TaskItem> tasksByType(String userId, TaskType type) {
        return taskRepository.findAllByUserIdAndTypeAndStatusOrderByDeadlineAsc(userId, type, TaskStatus.OPEN)
                .map(this::toDomain);
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

    private TaskItem toDomain(TaskDocument doc) {
        return new TaskItem(doc.getId(), doc.getUserId(), doc.getTitle(), doc.getType(),
                doc.getPriority(), doc.getStatus(), doc.getDeadline(), doc.getCreatedAt(), doc.getUpdatedAt());
    }
}
