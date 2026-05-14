package com.timetablebot.application.schedule;

import com.timetablebot.application.schedule.ExternalTimetableClient;
import com.timetablebot.infrastructure.schedule.ImportHistoryDocument;
import com.timetablebot.infrastructure.schedule.ImportHistoryRepository;
import com.timetablebot.infrastructure.schedule.ScheduleEventDocument;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScheduleModuleImportTest {

    @Test
    void shouldImportCsvEventsWhenValid() {
        ScheduleEventRepository repository = mock(ScheduleEventRepository.class);
        when(repository.countByUserId("u1")).thenReturn(Mono.just(3L));
        when(repository.save(any(ScheduleEventDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ImportHistoryRepository historyRepository = mock(ImportHistoryRepository.class);
        when(historyRepository.save(any(ImportHistoryDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        ExternalTimetableClient externalTimetableClient = mock(ExternalTimetableClient.class);
        ScheduleModule module = new ScheduleModule(repository, historyRepository, externalTimetableClient);
        String csv = "LESSON,Math,A-101,2026-05-14T10:00:00Z,2026-05-14T11:00:00Z\n" +
                "EXAM,Physics,B-210,2026-05-15T08:00:00Z,2026-05-15T10:00:00Z";

        StepVerifier.create(module.importFromCsv("u1", csv))
                .expectNext(2)
                .verifyComplete();

        verify(repository, times(1)).countByUserId("u1");
        verify(repository, times(2)).save(any(ScheduleEventDocument.class));
        verify(historyRepository, times(1)).save(any(ImportHistoryDocument.class));
    }

    @Test
    void shouldImportIcalEventsWhenValid() {
        ScheduleEventRepository repository = mock(ScheduleEventRepository.class);
        when(repository.countByUserId("u1")).thenReturn(Mono.just(0L));
        when(repository.save(any(ScheduleEventDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ImportHistoryRepository historyRepository = mock(ImportHistoryRepository.class);
        when(historyRepository.save(any(ImportHistoryDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        ExternalTimetableClient externalTimetableClient = mock(ExternalTimetableClient.class);
        ScheduleModule module = new ScheduleModule(repository, historyRepository, externalTimetableClient);
        String ical = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Physics Exam\nLOCATION:B-210\nDTSTART:20260515T080000Z\nDTEND:20260515T100000Z\nCATEGORIES:EXAM\nEND:VEVENT\nEND:VCALENDAR";

        StepVerifier.create(module.importFromIcal("u1", ical))
                .expectNext(1)
                .verifyComplete();

        verify(repository, times(1)).save(any(ScheduleEventDocument.class));
        verify(historyRepository, times(1)).save(any(ImportHistoryDocument.class));
    }

    @Test
    void shouldRejectSecondImportWhileFirstIsRunning() {
        ScheduleEventRepository repository = mock(ScheduleEventRepository.class);
        when(repository.countByUserId("u1")).thenReturn(Mono.just(0L));

        Sinks.One<ScheduleEventDocument> gate = Sinks.one();
        when(repository.save(any(ScheduleEventDocument.class))).thenReturn(gate.asMono());

        ImportHistoryRepository historyRepository = mock(ImportHistoryRepository.class);
        when(historyRepository.save(any(ImportHistoryDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ExternalTimetableClient externalTimetableClient = mock(ExternalTimetableClient.class);
        ScheduleModule module = new ScheduleModule(repository, historyRepository, externalTimetableClient);
        String csv = "LESSON,Math,A-101,2026-05-14T10:00:00Z,2026-05-14T11:00:00Z";

        var first = module.importFromCsv("u1", csv).subscribe();

        StepVerifier.create(module.importFromCsv("u1", csv))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().contains("Импорт уже выполняется"))
                .verify();

        gate.tryEmitValue(new ScheduleEventDocument());
        first.dispose();
    }

    @Test
    void shouldFailImportWhenCsvHasInvalidRow() {
        ScheduleEventRepository repository = mock(ScheduleEventRepository.class);
        ImportHistoryRepository historyRepository = mock(ImportHistoryRepository.class);
        when(historyRepository.save(any(ImportHistoryDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        ExternalTimetableClient externalTimetableClient = mock(ExternalTimetableClient.class);
        ScheduleModule module = new ScheduleModule(repository, historyRepository, externalTimetableClient);

        String csv = "LESSON,Math,A-101,wrong_date,2026-05-14T11:00:00Z";

        StepVerifier.create(module.importFromCsv("u1", csv))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                        && ex.getMessage().contains("Импорт не содержит валидных событий"))
                .verify();

        verify(repository, never()).countByUserId(anyString());
        verify(repository, never()).save(any());
        verify(historyRepository, times(1)).save(any(ImportHistoryDocument.class));
    }

    @Test
    void shouldFailImportWhenLimitExceeded() {
        ScheduleEventRepository repository = mock(ScheduleEventRepository.class);
        when(repository.countByUserId("u1")).thenReturn(Mono.just(5000L));

        ImportHistoryRepository historyRepository = mock(ImportHistoryRepository.class);
        when(historyRepository.save(any(ImportHistoryDocument.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        ExternalTimetableClient externalTimetableClient = mock(ExternalTimetableClient.class);
        ScheduleModule module = new ScheduleModule(repository, historyRepository, externalTimetableClient);
        String csv = "LESSON,Math,A-101,2026-05-14T10:00:00Z,2026-05-14T11:00:00Z";

        StepVerifier.create(module.importFromCsv("u1", csv))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().contains("Лимит событий"))
                .verify();

        verify(repository, times(1)).countByUserId("u1");
        verify(repository, never()).save(any());
        verify(historyRepository, times(1)).save(any(ImportHistoryDocument.class));
    }
}