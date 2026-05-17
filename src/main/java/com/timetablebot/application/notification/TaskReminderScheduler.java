package com.timetablebot.application.notification;

import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.infrastructure.notification.NotificationPublisher;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TaskReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);

    private final TaskRepository taskRepository;
    private final NotificationPublisher notificationPublisher;
    private final long leadMinutes;

    public TaskReminderScheduler(TaskRepository taskRepository,
                                 NotificationPublisher notificationPublisher,
                                 @Value("${notifications.task-reminder-lead-minutes:30}") long leadMinutes) {
        this.taskRepository = taskRepository;
        this.notificationPublisher = notificationPublisher;
        this.leadMinutes = leadMinutes;
    }

    @Scheduled(fixedDelayString = "${notifications.scheduler-fixed-delay-ms:60000}")
    public void scheduleUpcomingTaskReminders() {
        Instant now = Instant.now();
        Instant until = now.plusSeconds(Math.max(1, leadMinutes) * 60);

        taskRepository.findAllByStatusAndDeadlineBetweenOrderByDeadlineAsc(TaskStatus.OPEN, now, until)
                .doOnNext(task -> notificationPublisher.publishTask(new NotificationTaskPayload(
                        task.getUserId(),
                        task.getId(),
                        task.getTitle(),
                        task.getDeadline(),
                        "Напоминание: задача \"" + task.getTitle() + "\" скоро дедлайн в " + task.getDeadline()
                )))
                .doOnError(ex -> log.warn("Failed to schedule task reminders", ex))
                .onErrorResume(ex -> reactor.core.publisher.Flux.empty())
                .subscribe();
    }
}