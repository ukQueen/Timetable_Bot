package com.timetablebot.application.schedule;

import com.timetablebot.domain.schedule.EventType;
import com.timetablebot.domain.schedule.ScheduleEvent;
import com.timetablebot.infrastructure.schedule.ScheduleEventDocument;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;

@Component
public class ScheduleModule {
    private static final long MAX_EVENTS_PER_USER = 5000;

    private final ScheduleEventRepository repository;

    public ScheduleModule(ScheduleEventRepository repository) {
        this.repository = repository;
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
}
