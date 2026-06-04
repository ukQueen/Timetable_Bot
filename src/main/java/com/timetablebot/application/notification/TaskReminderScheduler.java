package com.timetablebot.application.notification;

import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.infrastructure.notification.NotificationPublisher;
import com.timetablebot.infrastructure.schedule.ScheduleEventDocument;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class TaskReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));

        private static final long WINDOW_SEC = 30;
    private static final long DAY_SEC    = 24 * 60 * 60;
    private static final long HOUR_SEC   = 60 * 60;

    private final TaskRepository taskRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final NotificationPublisher notificationPublisher;

    public TaskReminderScheduler(TaskRepository taskRepository,
                                 ScheduleEventRepository scheduleEventRepository,
                                 NotificationPublisher notificationPublisher) {
        this.taskRepository = taskRepository;
        this.scheduleEventRepository = scheduleEventRepository;
        this.notificationPublisher = notificationPublisher;
    }

        @Scheduled(fixedDelay = 30_000)
    public void scheduleReminders() {
        Instant now = Instant.now();
        log.info("Running reminder scheduler at {}", now);
        sendTask24hReminders(now);
        sendTask1hReminders(now);
        sendEventReminders(now);
    }

        @Scheduled(fixedDelay = 600_000)
    public void cleanupPastEvents() {
        Instant now = Instant.now();
        scheduleEventRepository.findAllByEndsAtBefore(now)
                .flatMap(event -> scheduleEventRepository.deleteById(event.getId()))
                .doOnError(ex -> log.warn("Failed to cleanup past events", ex))
                .onErrorResume(ex -> Flux.empty())
                .subscribe();
    }

    private void sendTask24hReminders(Instant now) {
        Instant windowStart = now.plusSeconds(DAY_SEC - WINDOW_SEC);
        Instant windowEnd   = now.plusSeconds(DAY_SEC + WINDOW_SEC);

        taskRepository.findAllByStatusAndDeadlineBetween(TaskStatus.OPEN, windowStart, windowEnd)
                .filter(task -> !task.isReminder24hSent())
                .flatMap(task -> {
                    String msg = "⏰ Напоминание за 24 часа: задача \"" + task.getTitle() + "\"\n"
                            + "Дедлайн: " + FMT.format(task.getDeadline()) + " (МСК)";
                    log.info("Sending 24h reminder for task '{}' to user {}", task.getTitle(), task.getUserId());
                    notificationPublisher.publishTask(new NotificationTaskPayload(
                            task.getUserId(), task.getId(), task.getTitle(), task.getDeadline(), msg));
                    task.setReminder24hSent(true);
                    task.setLastReminderSentAt(now);
                    task.setUpdatedAt(now);
                    return taskRepository.save(task);
                })
                .doOnError(ex -> log.warn("Failed to send 24h task reminders", ex))
                .onErrorResume(ex -> Flux.empty())
                .subscribe();
    }

    private void sendTask1hReminders(Instant now) {
        Instant windowStart = now.plusSeconds(HOUR_SEC - WINDOW_SEC);
        Instant windowEnd   = now.plusSeconds(HOUR_SEC + WINDOW_SEC);

        taskRepository.findAllByStatusAndDeadlineBetween(TaskStatus.OPEN, windowStart, windowEnd)
                .filter(task -> !task.isReminder1hSent())
                .flatMap(task -> {
                    String msg = "🔔 Напоминание за 1 час: задача \"" + task.getTitle() + "\"\n"
                            + "Дедлайн: " + FMT.format(task.getDeadline()) + " (МСК)";
                    log.info("Sending 1h reminder for task '{}' to user {}", task.getTitle(), task.getUserId());
                    notificationPublisher.publishTask(new NotificationTaskPayload(
                            task.getUserId(), task.getId(), task.getTitle(), task.getDeadline(), msg));
                    task.setReminder1hSent(true);
                    task.setLastReminderSentAt(now);
                    task.setUpdatedAt(now);
                    return taskRepository.save(task);
                })
                .doOnError(ex -> log.warn("Failed to send 1h task reminders", ex))
                .onErrorResume(ex -> Flux.empty())
                .subscribe();
    }

    private void sendEventReminders(Instant now) {
       
        sendEventsInWindow(now, DAY_SEC,  "📅 Напоминание за 24 часа");
       
        sendEventsInWindow(now, HOUR_SEC, "📌 Напоминание за 1 час");
    }

    private void sendEventsInWindow(Instant now, long leadSec, String prefix) {
        Instant windowStart = now.plusSeconds(leadSec - WINDOW_SEC);
        Instant windowEnd   = now.plusSeconds(leadSec + WINDOW_SEC);

        scheduleEventRepository.findAllByStartsAtBetween(windowStart, windowEnd)
                .filter(event -> leadSec == DAY_SEC
                        ? !event.isReminder24hSent()
                        : !event.isReminder1hSent())
                .flatMap(event -> {
                    String typeLabel = event.getType() != null
                            ? (event.getType().name().equals("EXAM") ? "экзамен" : "занятие")
                            : "событие";
                    String msg = prefix + ": " + typeLabel + " \"" + event.getTitle() + "\"\n"
                            + "Начало: " + FMT.format(event.getStartsAt()) + " (МСК)"
                            + (event.getPlace() != null && !event.getPlace().isBlank()
                                ? "\nМесто: " + event.getPlace() : "");
                    log.info("Sending event reminder for '{}' to user {}", event.getTitle(), event.getUserId());
                    notificationPublisher.publishTask(new NotificationTaskPayload(
                            event.getUserId(), event.getId(), event.getTitle(), event.getStartsAt(), msg));
                    if (leadSec == DAY_SEC) {
                        event.setReminder24hSent(true);
                    } else {
                        event.setReminder1hSent(true);
                    }
                    return scheduleEventRepository.save(event);
                })
                .doOnError(ex -> log.warn("Failed to send event reminders", ex))
                .onErrorResume(ex -> Flux.empty())
                .subscribe();
    }
}
