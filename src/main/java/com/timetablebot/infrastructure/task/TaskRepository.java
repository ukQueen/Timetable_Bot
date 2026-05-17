package com.timetablebot.infrastructure.task;

import com.timetablebot.domain.user.TaskStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface TaskRepository extends ReactiveCrudRepository<TaskDocument, String> {
    Flux<TaskDocument> findAllByUserIdAndDeadlineBetweenOrderByDeadlineAsc(String userId, Instant from, Instant to);
    Mono<TaskDocument> findByIdAndUserId(String id, String userId);
    Mono<Void> deleteByIdAndUserId(String id, String userId);
    Flux<TaskDocument> findAllByUserIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(String userId, TaskStatus status, Instant before);
    Flux<TaskDocument> findAllByStatusAndDeadlineBetweenOrderByDeadlineAsc(TaskStatus status, Instant from, Instant to);
}