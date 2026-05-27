package com.timetablebot.application.notification;

import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.infrastructure.notification.NotificationPublisher;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskReminderSchedulerTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ScheduleEventRepository scheduleEventRepository;
    @Mock private NotificationPublisher notificationPublisher;

    @Test
    void shouldPublishReminderForTaskDueInOneHour() {
        TaskDocument task = new TaskDocument();
        task.setId("t1");
        task.setUserId("1001");
        task.setTitle("Lab work");
        task.setStatus(TaskStatus.OPEN);
        task.setDeadline(Instant.now().plusSeconds(3600));
        task.setLastReminderSentAt(null);

        when(taskRepository.findAllByStatusAndDeadlineBetween(eq(TaskStatus.OPEN), any(), any()))
                .thenReturn(Flux.just(task));
        when(taskRepository.save(any(TaskDocument.class))).thenReturn(Mono.just(task));
        when(scheduleEventRepository.findAllByStartsAtBetween(any(), any())).thenReturn(Flux.empty());
        when(scheduleEventRepository.findAllByEndsAtBefore(any())).thenReturn(Flux.empty());

        TaskReminderScheduler scheduler = new TaskReminderScheduler(taskRepository, scheduleEventRepository, notificationPublisher);
        scheduler.scheduleReminders();

        verify(notificationPublisher, atLeastOnce()).publishTask(any());
    }

    @Test
    void shouldSkipTaskAlreadyRemindedRecently() {
        TaskDocument task = new TaskDocument();
        task.setId("t2");
        task.setUserId("1001");
        task.setTitle("Old reminder");
        task.setStatus(TaskStatus.OPEN);
        task.setDeadline(Instant.now().plusSeconds(3600));
        // Напоминание было 10 секунд назад — внутри окна, пропускаем
        task.setLastReminderSentAt(Instant.now().minusSeconds(10));

        when(taskRepository.findAllByStatusAndDeadlineBetween(eq(TaskStatus.OPEN), any(), any()))
                .thenReturn(Flux.just(task));
        when(scheduleEventRepository.findAllByStartsAtBetween(any(), any())).thenReturn(Flux.empty());
        when(scheduleEventRepository.findAllByEndsAtBefore(any())).thenReturn(Flux.empty());

        TaskReminderScheduler scheduler = new TaskReminderScheduler(taskRepository, scheduleEventRepository, notificationPublisher);
        scheduler.scheduleReminders();

        verify(notificationPublisher, never()).publishTask(any());
    }
}
