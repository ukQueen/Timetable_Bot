package com.timetablebot.application.telegram;

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

    @Mock
    private UserModule userModule;
    @Mock
    private ScheduleModule scheduleModule;
    @Mock
    private TaskModule taskModule;

    @Test
    void shouldReturnImportHistoryForImportsCommand() {
        MessageHandlerModule module = new MessageHandlerModule(userModule, scheduleModule, taskModule);
        ImportHistoryItem history = new ImportHistoryItem(
                "csv",
                ImportStatus.SUCCESS,
                10,
                null,
                Instant.parse("2026-05-17T10:00:00Z")
        );

        when(scheduleModule.importHistory("1001")).thenReturn(Flux.just(history));

        TelegramUpdateRequest update = new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(
                        1L,
                        new TelegramUpdateRequest.Chat(1001L),
                        new TelegramUpdateRequest.User(42L, "u", "f", "l"),
                        "/imports"
                )
        );

        StepVerifier.create(module.handle(update))
                .expectNextMatches(response -> isExpectedImportResponse(response, "SUCCESS", "imported=10"))
                .verifyComplete();
    }

    @Test
    void shouldIncludeErrorMessageInImportHistoryResponse() {
        MessageHandlerModule module = new MessageHandlerModule(userModule, scheduleModule, taskModule);
        ImportHistoryItem history = new ImportHistoryItem(
                "ical",
                ImportStatus.ERROR,
                0,
                "VEVENT #2: не хватает обязательных полей VEVENT",
                Instant.parse("2026-05-17T11:00:00Z")
        );

        when(scheduleModule.importHistory("1001")).thenReturn(Flux.just(history));

        TelegramUpdateRequest update = new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(
                        1L,
                        new TelegramUpdateRequest.Chat(1001L),
                        new TelegramUpdateRequest.User(42L, "u", "f", "l"),
                        "/imports"
                )
        );

        StepVerifier.create(module.handle(update))
                .expectNextMatches(response ->
                        "ok".equals(response.status())
                                && response.message().contains("ОШИБКА")
                                && response.message().contains("error=VEVENT #2"))
                .verifyComplete();
    }

    @Test
    void shouldTruncateLongImportErrorMessage() {
        MessageHandlerModule module = new MessageHandlerModule(userModule, scheduleModule, taskModule);
        String longError = "E".repeat(300);
        ImportHistoryItem history = new ImportHistoryItem(
                "csv",
                ImportStatus.ERROR,
                0,
                longError,
                Instant.parse("2026-05-17T12:00:00Z")
        );
        when(scheduleModule.importHistory("1001")).thenReturn(Flux.just(history));

        TelegramUpdateRequest update = new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(1L, new TelegramUpdateRequest.Chat(1001L), new TelegramUpdateRequest.User(42L, "u", "f", "l"), "/imports")
        );

        StepVerifier.create(module.handle(update))
                .expectNextMatches(response -> "ok".equals(response.status())
                        && response.message().contains("...")
                        && !response.message().contains("E".repeat(250)))
                .verifyComplete();
    }

    @Test
    void menuShouldContainImportsCommand() {
        MessageHandlerModule module = new MessageHandlerModule(userModule, scheduleModule, taskModule);
        TelegramUpdateRequest update = new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(
                        1L,
                        new TelegramUpdateRequest.Chat(1001L),
                        new TelegramUpdateRequest.User(42L, "u", "f", "l"),
                        "/menu"
                )
        );

        StepVerifier.create(module.handle(update))
                .expectNextMatches(response -> "ok".equals(response.status()) && response.message().contains("/imports"))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyHistoryMessageForImportsCommand() {
        MessageHandlerModule module = new MessageHandlerModule(userModule, scheduleModule, taskModule);
        when(scheduleModule.importHistory("1001")).thenReturn(Flux.empty());

        TelegramUpdateRequest update = new TelegramUpdateRequest(
                new TelegramUpdateRequest.Message(
                        1L,
                        new TelegramUpdateRequest.Chat(1001L),
                        new TelegramUpdateRequest.User(42L, "u", "f", "l"),
                        "/imports"
                )
        );

        StepVerifier.create(module.handle(update))
                .expectNext(BotMessageResponse.ok("Импорты: история пуста."))
                .verifyComplete();
    }

    private boolean isExpectedImportResponse(BotMessageResponse response, String status, String importedCount) {
        return response != null
                && "ok".equals(response.status())
                && response.message().contains("Последние импорты:")
                && response.message().contains(status)
                && response.message().contains(importedCount);
    }
}
