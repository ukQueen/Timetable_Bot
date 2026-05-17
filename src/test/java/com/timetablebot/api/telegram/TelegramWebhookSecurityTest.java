package com.timetablebot.api.telegram;

import com.timetablebot.application.schedule.ExternalTimetableClient;
import com.timetablebot.infrastructure.schedule.ImportHistoryRepository;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import com.timetablebot.infrastructure.task.TaskRepository;
import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import com.timetablebot.infrastructure.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "telegram.bot.webhook-secret=test-secret")
@AutoConfigureWebTestClient
class TelegramWebhookSecurityTest {

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

    @Test
    void shouldRejectWebhookWithoutSecretHeader() {
        given(telegramBotClient.sendMessage(any(), any())).willReturn(Mono.empty());

        webTestClient.post().uri("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/menu\"}}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo("error");
    }

    @Test
    void shouldAcceptWebhookWithValidSecretHeader() {
        given(telegramBotClient.sendMessage(any(), any())).willReturn(Mono.empty());

        webTestClient.post().uri("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":{\"chat\":{\"id\":1001},\"text\":\"/menu\"}}")
                .exchange()
                .expectStatus().isOk();
    }
}