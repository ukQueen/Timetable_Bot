package com.timetablebot.application.schedule;

import com.timetablebot.infrastructure.schedule.ImportHistoryDocument;
import com.timetablebot.infrastructure.schedule.ImportHistoryRepository;
import com.timetablebot.infrastructure.schedule.ScheduleEventDocument;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleImportParsingTest {

    @Mock
    private ScheduleEventRepository scheduleEventRepository;
    @Mock
    private ImportHistoryRepository importHistoryRepository;
    @Mock
    private ExternalTimetableClient externalTimetableClient;

    @Test
    void shouldImportValidCsv() {
        ScheduleModule module = new ScheduleModule(scheduleEventRepository, importHistoryRepository, externalTimetableClient);

        when(scheduleEventRepository.countByUserId("1001")).thenReturn(Mono.just(0L));
        when(scheduleEventRepository.save(any(ScheduleEventDocument.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(importHistoryRepository.save(any(ImportHistoryDocument.class))).thenReturn(Mono.just(new ImportHistoryDocument()));

        String csv = "LESSON,Math,A-101,2026-05-13T10:00:00Z,2026-05-13T11:00:00Z";

        StepVerifier.create(module.importFromCsv("1001", csv))
                .expectNext(1)
                .verifyComplete();

        verify(scheduleEventRepository, times(1)).save(any(ScheduleEventDocument.class));
    }

    @Test
    void shouldFailOnInvalidCsv() {
        ScheduleModule module = new ScheduleModule(scheduleEventRepository, importHistoryRepository, externalTimetableClient);

        when(importHistoryRepository.save(any(ImportHistoryDocument.class))).thenReturn(Mono.just(new ImportHistoryDocument()));

        String csv = "LESSON,Math,A-101,not-an-instant,2026-05-13T11:00:00Z";

        StepVerifier.create(module.importFromCsv("1001", csv))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void shouldImportValidIcal() {
        ScheduleModule module = new ScheduleModule(scheduleEventRepository, importHistoryRepository, externalTimetableClient);

        when(scheduleEventRepository.countByUserId("1001")).thenReturn(Mono.just(0L));
        when(scheduleEventRepository.save(any(ScheduleEventDocument.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(importHistoryRepository.save(any(ImportHistoryDocument.class))).thenReturn(Mono.just(new ImportHistoryDocument()));

        String ical = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Physics\nLOCATION:B-210\nDTSTART:20260515T080000Z\nDTEND:20260515T100000Z\nCATEGORIES:EXAM\nEND:VEVENT\nEND:VCALENDAR";

        StepVerifier.create(module.importFromIcal("1001", ical))
                .expectNext(1)
                .verifyComplete();

        verify(scheduleEventRepository, times(1)).save(any(ScheduleEventDocument.class));
    }
}