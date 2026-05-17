package com.timetablebot.api.telegram;

import com.timetablebot.domain.schedule.EventType;
import com.timetablebot.domain.user.OnboardingStatus;
import com.timetablebot.domain.user.WeekStartPreference;
import com.timetablebot.application.schedule.ExternalTimetableClient;
import com.timetablebot.infrastructure.schedule.ImportHistoryDocument;
import com.timetablebot.infrastructure.schedule.ImportHistoryRepository;

import com.timetablebot.infrastructure.schedule.ScheduleEventDocument;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import com.timetablebot.infrastructure.notification.NotificationPublisher;

import com.timetablebot.infrastructure.task.TaskDocument;
import com.timetablebot.infrastructure.task.TaskRepository;

import com.timetablebot.infrastructure.user.UserDocument;
import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import com.timetablebot.infrastructure.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureWebTestClient
class TelegramWebhookControllerTest {
    @Autowired
    private WebTestClient webTestClient;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private ScheduleEventRepository scheduleEventRepository;

    @MockBean
    private ImportHistoryRepository importHistoryRepository;
    @MockBean
    private ExternalTimetableClient externalTimetableClient;
    @MockBean
    private TaskRepository taskRepository;
    @MockBean
    private TelegramBotClient telegramBotClient;
    @MockBean
    private NotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        UserDocument userDocument = new UserDocument();
        userDocument.setId("1001");
        userDocument.setTimezone("UTC");
        userDocument.setWeekStartPreference(WeekStartPreference.MONDAY);
        userDocument.setOnboardingStatus(OnboardingStatus.NEW);
        userDocument.setCreatedAt(Instant.now());
        userDocument.setUpdatedAt(Instant.now());

        ScheduleEventDocument event = new ScheduleEventDocument();
        event.setId("e1");
        event.setUserId("1001");
        event.setTitle("Math");
        event.setPlace("A-101");
        event.setType(EventType.LESSON);
        event.setStartsAt(Instant.parse("2026-05-13T10:00:00Z"));
        event.setEndsAt(Instant.parse("2026-05-13T11:00:00Z"));

        given(userRepository.findById(any(String.class))).willReturn(Mono.just(userDocument));
        given(userRepository.save(any(UserDocument.class))).willReturn(Mono.just(userDocument));
        given(scheduleEventRepository.findAllByUserIdAndStartsAtBetween(any(), any(), any())).willReturn(Flux.empty());
        given(scheduleEventRepository.countByUserId(any())).willReturn(Mono.just(1L));
        given(scheduleEventRepository.save(any(ScheduleEventDocument.class))).willReturn(Mono.just(event));
        given(scheduleEventRepository.findByIdAndUserId(eq("e1"), any())).willReturn(Mono.just(event));
        given(scheduleEventRepository.deleteByIdAndUserId(eq("e1"), any())).willReturn(Mono.empty());
        given(importHistoryRepository.save(any(ImportHistoryDocument.class))).willReturn(Mono.just(new ImportHistoryDocument()));
        given(externalTimetableClient.download(eq("https://example.com/timetable.csv"))).willReturn(Mono.just("LESSON,Math,A-101,2026-05-13T10:00:00Z,2026-05-13T11:00:00Z"));

        TaskDocument task = new TaskDocument();
        task.setId("t1");
        task.setUserId("1001");
        task.setTitle("HW");
        task.setDeadline(Instant.parse("2026-05-13T12:00:00Z"));
        given(taskRepository.save(any(TaskDocument.class))).willReturn(Mono.just(task));
        given(taskRepository.findAllByUserIdAndDeadlineBetweenOrderByDeadlineAsc(any(), any(), any())).willReturn(Flux.just(task));
        given(taskRepository.findByIdAndUserId(eq("t1"), any())).willReturn(Mono.just(task));
        given(taskRepository.findAllByUserIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(any(), any(), any())).willReturn(Flux.just(task));
        given(taskRepository.deleteByIdAndUserId(eq("t1"), any())).willReturn(Mono.empty());
        given(telegramBotClient.sendMessage(any(), any())).willReturn(Mono.empty());
    }

    @Test
    void shouldAddEvent() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/add_event Math | A-101 | 2026-05-13T10:00:00Z | 2026-05-13T11:00:00Z | LESSON\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok");
    }


    @Test
    void shouldImportTimetableFromCsv() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/import_timetable LESSON,Math,A-101,2026-05-13T10:00:00Z,2026-05-13T11:00:00Z\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.message").isEqualTo("Импорт завершен. Загружено событий: 1");
    }

    @Test
    void shouldImportTimetableFromIcal() {
        String ical = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Physics Exam\nLOCATION:B-210\nDTSTART:20260515T080000Z\nDTEND:20260515T100000Z\nCATEGORIES:EXAM\nEND:VEVENT\nEND:VCALENDAR";
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/import_timetable " + ical.replace("\n", "\\n") + "\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.message").isEqualTo("Импорт завершен. Загружено событий: 1");
    }

    @Test
    void shouldImportTimetableFromExternalApi() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/import_external https://example.com/timetable.csv\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.message").isEqualTo("Импорт из API завершен. Загружено событий: 1");
    }

    @Test
    void shouldManageTasks() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/add_task HW | 2026-05-13T12:00:00Z | HIGH | HOMEWORK\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");

        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/tasks_today\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");

        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/done_task t1\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void shouldReturnResponseEvenWhenTelegramSendFails() {
        given(telegramBotClient.sendMessage(any(), any())).willReturn(Mono.error(new RuntimeException("telegram down")));

        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/menu\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok");

        verify(telegramBotClient).sendMessage(eq(1001L), any());
    }
    @Test
    void shouldEditAndListOverdueTasks() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/edit_task t1 | Updated HW | 2026-05-14T12:00:00Z | MEDIUM | LAB\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");

        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/tasks_overdue\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void shouldAcceptLowercaseEnumsForTaskCommands() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/add_task HW lower | 2026-05-13T12:00:00Z | high | homework\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");

        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/edit_task t1 | Updated Lower | 2026-05-14T12:00:00Z | medium | lab\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void shouldDeleteOwnEvent() {
        webTestClient.post().uri("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/delete_event e1\"}}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("ok");
    }
}
