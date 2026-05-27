package com.timetablebot.infrastructure.schedule;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ScheduleEventRepository extends ReactiveCrudRepository<ScheduleEventDocument, String> {
    Flux<ScheduleEventDocument> findAllByUserIdAndStartsAtBetween(String userId, Instant fromInclusive, Instant toExclusive);
    Flux<ScheduleEventDocument> findAllByUserIdOrderByStartsAtAsc(String userId);
        Flux<ScheduleEventDocument> findAllByUserIdAndEndsAtAfterOrderByStartsAtAsc(String userId, Instant after);
        Flux<ScheduleEventDocument> findAllByStartsAtBetween(Instant from, Instant to);
        Flux<ScheduleEventDocument> findAllByEndsAtBefore(Instant before);
    Mono<Long> countByUserId(String userId);
    Mono<ScheduleEventDocument> findByIdAndUserId(String id, String userId);
    Mono<Void> deleteByIdAndUserId(String id, String userId);
}
