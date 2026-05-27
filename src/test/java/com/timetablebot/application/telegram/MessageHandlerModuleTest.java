package com.timetablebot.application.telegram;

import com.timetablebot.application.llm.LlmAdvisorModule;
import com.timetablebot.application.schedule.ScheduleModule;
import com.timetablebot.application.task.TaskModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import com.timetablebot.domain.schedule.ImportHistoryItem;
import com.timetablebot.domain.schedule.ImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageHandlerModuleTest {

    @Mock private UserModule userModule;
    @Mock private ScheduleModule scheduleModule;
    @Mock private TaskModule taskModule;
    @Mock private LlmAdvisorModule llmAdvisorModule;

    private MessageHandlerModule module() {
        return new MessageHandlerModule(userModule, scheduleModule, taskModule, llmAdvisorModule);
    }

    private TelegramUpdateRequest update(String text) {
        return new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(1L,
                        new TelegramUpdateRequest.Chat(1001L),
                        new TelegramUpdateRequest.User(42L, "u", "f", "l"), text, null));
    }

    @Test
    void menuShouldContainAllCommands() {
        StepVerifier.create(module().handle(update("/menu")))
                .expectNextMatches(r -> "ok".equals(r.status())
                        && r.message().contains("/today")
                        && r.message().contains("/add_task")
                        && r.message().contains("/ask"))
                .verifyComplete();
    }

    @Test
    void shouldReturnImportHistoryForImportsCommand() {
        ImportHistoryItem history = new ImportHistoryItem("csv", ImportStatus.SUCCESS, 5, null, Instant.parse("2026-05-17T10:00:00Z"));
        when(scheduleModule.importHistory("1001")).thenReturn(Flux.just(history));

        StepVerifier.create(module().handle(update("/imports")))
                .expectNextMatches(r -> "ok".equals(r.status()) && r.message().contains("УСПЕХ"))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyImportHistory() {
        when(scheduleModule.importHistory("1001")).thenReturn(Flux.empty());
        StepVerifier.create(module().handle(update("/imports")))
                .expectNext(BotMessageResponse.ok("Импорты: история пуста."))
                .verifyComplete();
    }

    @Test
    void helpShouldContainExamples() {
        StepVerifier.create(module().handle(update("/help")))
                .expectNextMatches(r -> "ok".equals(r.status()) && r.message().contains("LESSON"))
                .verifyComplete();
    }
}
