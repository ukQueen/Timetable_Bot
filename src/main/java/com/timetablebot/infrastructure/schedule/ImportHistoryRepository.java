package com.timetablebot.infrastructure.schedule;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ImportHistoryRepository extends ReactiveCrudRepository<ImportHistoryDocument, String> {
    Flux<ImportHistoryDocument> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}