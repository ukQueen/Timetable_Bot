package com.timetablebot.application.notification;

import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.infrastructure.notification.NotificationPublisher;
import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReminderSchedulerTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private TaskReminderScheduler scheduler = new TaskReminderScheduler(taskRepository, notificationPublisher, 30);

    @Test
    void shouldPublishAndMarkTaskWhenReminderNotSent() {
        TaskDocument task = new TaskDocument();
        task.setId("t1");
        task.setUserId("1001");
        task.setTitle("Prepare report");
        task.setStatus(TaskStatus.OPEN);
        task.setDeadline(Instant.now().plusSeconds(600));
        task.setLastReminderSentAt(null);

        when(taskRepository.findAllByStatusAndDeadlineBetweenOrderByDeadlineAsc(eq(TaskStatus.OPEN), any(), any()))
                .thenReturn(Flux.just(task));
        when(taskRepository.save(any(TaskDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        scheduler.scheduleUpcomingTaskReminders();

        verify(notificationPublisher).publishTask(any(NotificationTaskPayload.class));

        ArgumentCaptor<TaskDocument> savedCaptor = ArgumentCaptor.forClass(TaskDocument.class);
        verify(taskRepository).save(savedCaptor.capture());
        assertNotNull(savedCaptor.getValue().getLastReminderSentAt());
    }

    @Test
    void shouldSkipTaskWhenReminderAlreadySent() {
        TaskDocument task = new TaskDocument();
        task.setId("t2");
        task.setUserId("1002");
        task.setTitle("Already reminded");
        task.setStatus(TaskStatus.OPEN);
        task.setDeadline(Instant.now().plusSeconds(600));
        task.setLastReminderSentAt(Instant.now().minusSeconds(60));

        when(taskRepository.findAllByStatusAndDeadlineBetweenOrderByDeadlineAsc(eq(TaskStatus.OPEN), any(), any()))
                .thenReturn(Flux.just(task));

        scheduler.scheduleUpcomingTaskReminders();

        verify(notificationPublisher, never()).publishTask(any(NotificationTaskPayload.class));
        verify(taskRepository, never()).save(any(TaskDocument.class));
    }
}