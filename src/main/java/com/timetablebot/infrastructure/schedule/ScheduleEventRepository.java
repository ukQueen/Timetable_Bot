package com.timetablebot.infrastructure.schedule;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ScheduleEventRepository extends ReactiveCrudRepository<ScheduleEventDocument, String> {
    Flux<ScheduleEventDocument> findAllByUserIdAndStartsAtBetween(String userId, Instant fromInclusive, Instant toExclusive);

    Mono<Long> countByUserId(String userId);

    Mono<ScheduleEventDocument> findByIdAndUserId(String id, String userId);

    Mono<Void> deleteByIdAndUserId(String id, String userId);
}
