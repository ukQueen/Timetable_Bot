package com.timetablebot.application.schedule;

import com.timetablebot.domain.schedule.EventType;
import com.timetablebot.domain.schedule.ImportHistoryItem;
import com.timetablebot.domain.schedule.ImportStatus;
import com.timetablebot.domain.schedule.ScheduleEvent;
import com.timetablebot.infrastructure.schedule.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScheduleModule {
    private static final long MAX_EVENTS_PER_USER = 5000;

    private final ScheduleEventRepository repository;
    private final ImportHistoryRepository importHistoryRepository;
    private final ExternalTimetableClient externalTimetableClient;
    private final Set<String> activeImports = ConcurrentHashMap.newKeySet();

    public ScheduleModule(ScheduleEventRepository repository, ImportHistoryRepository importHistoryRepository, ExternalTimetableClient externalTimetableClient) {
        this.repository = repository;
        this.importHistoryRepository = importHistoryRepository;
        this.externalTimetableClient = externalTimetableClient;
    }

    public Flux<ScheduleEvent> eventsForToday(String userId, ZoneId zoneId) {
        LocalDate now = LocalDate.now(zoneId);
        return eventsByDateRange(userId, now, now.plusDays(1), zoneId);
    }

    public Flux<ScheduleEvent> eventsForTomorrow(String userId, ZoneId zoneId) {
        LocalDate tomorrow = LocalDate.now(zoneId).plusDays(1);
        return eventsByDateRange(userId, tomorrow, tomorrow.plusDays(1), zoneId);
    }

    public Flux<ScheduleEvent> eventsForWeek(String userId, ZoneId zoneId) {
        LocalDate start = LocalDate.now(zoneId);
        LocalDate end = start.plusDays(7);
        return eventsByDateRange(userId, start, end, zoneId);
    }

    public Mono<ScheduleEvent> createEvent(String userId, EventType type, String title, String place, String source, Instant startsAt, Instant endsAt) {
        return repository.countByUserId(userId)
                .flatMap(count -> count >= MAX_EVENTS_PER_USER
                        ? Mono.error(new IllegalStateException("Лимит событий (5000) достигнут."))
                        : saveNew(userId, type, title, place, source, startsAt, endsAt));
    }

    public Mono<Integer> importFromCsv(String userId, String csvContent) {
        return importEvents(userId, parseCsv(csvContent), "csv");
    }

    public Mono<Integer> importFromIcal(String userId, String icalContent) {
        return importEvents(userId, parseIcal(icalContent), "ical");
    }

    public Mono<Integer> importFromExternalApi(String userId, String url) {
        return externalTimetableClient.download(url)
                .flatMap(payload -> importEvents(userId, parseCsv(payload), "external_api"));
    }

    public Flux<ImportHistoryItem> importHistory(String userId) {
        return importHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toImportHistoryItem);
    }

    private Mono<Integer> importEvents(String userId, ParseResult parseResult, String source) {
        List<ImportedEvent> importedEvents = parseResult.events();
        if (importedEvents.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Импорт не содержит валидных событий."));
        }

        if (!activeImports.add(userId)) {
            return Mono.error(new IllegalStateException("Импорт уже выполняется. Дождитесь завершения текущего импорта."));
        }

        return repository.countByUserId(userId)
                .flatMap(existingCount -> {
                    if (existingCount + importedEvents.size() > MAX_EVENTS_PER_USER) {
                        return Mono.error(new IllegalStateException("Лимит событий (5000) превышен."));
                    }

                    return saveImportedBatch(userId, importedEvents)
                            .map(List::size);
                })
                .flatMap(count -> {
                    ImportStatus status = parseResult.errors().isEmpty() ? ImportStatus.SUCCESS : ImportStatus.PARTIAL;
                    String message = parseResult.errors().isEmpty() ? null : String.join("; ", parseResult.errors());
                    return saveImportHistory(userId, source, status, count, message).thenReturn(count);
                })
                .onErrorResume(ex -> saveImportHistory(userId, source, ImportStatus.ERROR, 0, ex.getMessage()).then(Mono.error(ex)))
                .doFinally(signalType -> activeImports.remove(userId));
    }

    private Mono<List<String>> saveImportedBatch(String userId, List<ImportedEvent> importedEvents) {
        List<String> createdIds = new ArrayList<>();
        return Flux.fromIterable(importedEvents)
                .concatMap(event -> saveNew(userId, event.type(), event.title(), event.place(), event.source(), event.startsAt(), event.endsAt()))
                .doOnNext(event -> createdIds.add(event.id()))
                .map(ScheduleEvent::id)
                .collectList()
                .onErrorResume(ex -> rollbackImportedBatch(userId, createdIds).then(Mono.error(ex)));
    }

    private Mono<Void> rollbackImportedBatch(String userId, List<String> createdIds) {
        if (createdIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(createdIds)
                .flatMap(id -> repository.deleteByIdAndUserId(id, userId))
                .then();
    }

    public Mono<ScheduleEvent> updateEvent(String userId, String eventId, EventType type, String title, String place, Instant startsAt, Instant endsAt) {
        return repository.findByIdAndUserId(eventId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Событие не найдено.")))
                .flatMap(doc -> {
                    doc.setType(type);
                    doc.setTitle(title);
                    doc.setPlace(place);
                    doc.setStartsAt(startsAt);
                    doc.setEndsAt(endsAt);
                    return repository.save(doc);
                })
                .map(this::toDomain);
    }

    public Mono<Void> deleteEvent(String userId, String eventId) {
        return repository.findByIdAndUserId(eventId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Событие не найдено.")))
                .flatMap(doc -> repository.deleteByIdAndUserId(doc.getId(), userId));
    }

    private ParseResult parseIcal(String icalContent) {
        String[] lines = icalContent.split("\r?\n");
        List<ImportedEvent> events = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        String summary = null;
        String location = null;
        String dtStart = null;
        String dtEnd = null;
        String categories = null;
        int veventIndex = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.equals("BEGIN:VEVENT")) {
                veventIndex++;
                summary = null; location = null; dtStart = null; dtEnd = null; categories = null;
            } else if (line.startsWith("SUMMARY:")) {
                summary = line.substring("SUMMARY:".length()).trim();
            } else if (line.startsWith("LOCATION:")) {
                location = line.substring("LOCATION:".length()).trim();
            } else if (line.startsWith("DTSTART")) {
                dtStart = line.substring(line.indexOf(':') + 1).trim();
            } else if (line.startsWith("DTEND")) {
                dtEnd = line.substring(line.indexOf(':') + 1).trim();
            } else if (line.startsWith("CATEGORIES:")) {
                categories = line.substring("CATEGORIES:".length()).trim();
            } else if (line.equals("END:VEVENT")) {
                try {
                    if (summary == null || dtStart == null || dtEnd == null) {
                        throw new IllegalArgumentException("не хватает обязательных полей VEVENT");
                    }
                    if (summary.isBlank()) {
                        throw new IllegalArgumentException("SUMMARY must not be blank");
                    }
                    Instant startsAt = parseIcalInstant(dtStart);
                    Instant endsAt = parseIcalInstant(dtEnd);
                    if (!endsAt.isAfter(startsAt)) {
                        throw new IllegalArgumentException("end must be after start");
                    }
                    EventType type = (categories != null && categories.toUpperCase().contains("EXAM")) ? EventType.EXAM : EventType.LESSON;
                    events.add(new ImportedEvent(type, summary, location == null ? "" : location, startsAt, endsAt, "ical"));
                } catch (Exception ex) {
                    errors.add("VEVENT #" + veventIndex + ": " + ex.getMessage());
                }
            }
        }

        return new ParseResult(events, errors);
    }

    private Instant parseIcalInstant(String value) {
        if (value.endsWith("Z")) {
            return Instant.parse(value.replaceFirst("(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z", "$1-$2-$3T$4:$5:$6Z"));
        }
        LocalDateTime dateTime = LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        return dateTime.toInstant(ZoneOffset.UTC);
    }

    private ParseResult parseCsv(String csvContent) {
        String[] lines = csvContent.split("\r?\n");
        List<ImportedEvent> events = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && line.toLowerCase().startsWith("type,")) {
                continue;
            }

            String[] parts = line.split(",", 5);
            if (parts.length != 5) {
                errors.add("Строка " + (i + 1) + ": ожидается 5 колонок");
                continue;
            }

            try {
                EventType type = EventType.valueOf(parts[0].trim().toUpperCase());
                String title = parts[1].trim();
                String place = parts[2].trim();
                Instant startsAt = Instant.parse(parts[3].trim());
                Instant endsAt = Instant.parse(parts[4].trim());
                if (title.isBlank()) {
                    throw new IllegalArgumentException("title must not be blank");
                }
                if (!endsAt.isAfter(startsAt)) {
                    throw new IllegalArgumentException("end must be after start");
                }
                events.add(new ImportedEvent(type, title, place, startsAt, endsAt, "csv"));
            } catch (Exception ex) {
                errors.add("Строка " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new ParseResult(events, errors);
    }

    private Mono<ScheduleEvent> saveNew(String userId, EventType type, String title, String place, String source, Instant startsAt, Instant endsAt) {
        ScheduleEventDocument doc = new ScheduleEventDocument();
        doc.setUserId(userId);
        doc.setType(type);
        doc.setTitle(title);
        doc.setPlace(place);
        doc.setSource(source);
        doc.setStartsAt(startsAt);
        doc.setEndsAt(endsAt);
        return repository.save(doc).map(this::toDomain);
    }

    private Flux<ScheduleEvent> eventsByDateRange(String userId, LocalDate from, LocalDate to, ZoneId zoneId) {
        Instant fromInstant = from.atStartOfDay(zoneId).toInstant();
        Instant toInstant = to.atStartOfDay(zoneId).toInstant();
        return repository.findAllByUserIdAndStartsAtBetween(userId, fromInstant, toInstant)
                .map(this::toDomain);
    }

    private ScheduleEvent toDomain(ScheduleEventDocument doc) {
        return new ScheduleEvent(doc.getId(), doc.getUserId(), doc.getType(), doc.getTitle(), doc.getPlace(), doc.getSource(), doc.getStartsAt(), doc.getEndsAt());
    }

    private Mono<Void> saveImportHistory(String userId, String source, ImportStatus status, Integer importedCount, String errorMessage) {
        ImportHistoryDocument history = new ImportHistoryDocument();
        history.setUserId(userId);
        history.setSource(source);
        history.setStatus(status);
        history.setImportedCount(importedCount);
        history.setErrorMessage(errorMessage);
        history.setCreatedAt(Instant.now());
        return importHistoryRepository.save(history).then();
    }

    private ImportHistoryItem toImportHistoryItem(ImportHistoryDocument doc) {
        return new ImportHistoryItem(
                doc.getSource(),
                doc.getStatus(),
                doc.getImportedCount(),
                doc.getErrorMessage(),
                doc.getCreatedAt()
        );
    }

    private record ParseResult(List<ImportedEvent> events, List<String> errors) {
    }

    private record ImportedEvent(EventType type, String title, String place, Instant startsAt, Instant endsAt, String source) {
    }
    
}
