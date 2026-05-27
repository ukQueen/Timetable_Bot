package com.timetablebot.infrastructure.task;

import com.timetablebot.domain.user.TaskPriority;
import com.timetablebot.domain.user.TaskStatus;
import com.timetablebot.domain.user.TaskType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface TaskRepository extends ReactiveCrudRepository<TaskDocument, String> {
        Flux<TaskDocument> findAllByUserIdAndStatusOrderByDeadlineAsc(String userId, TaskStatus status);
        Flux<TaskDocument> findAllByUserIdOrderByDeadlineAsc(String userId);
        Flux<TaskDocument> findAllByUserIdAndStatusAndDeadlineBetweenOrderByDeadlineAsc(String userId, TaskStatus status, Instant from, Instant to);
        Mono<TaskDocument> findByIdAndUserId(String id, String userId);
    Mono<Void> deleteByIdAndUserId(String id, String userId);
        Flux<TaskDocument> findAllByUserIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(String userId, TaskStatus status, Instant before);
        Flux<TaskDocument> findAllByStatusAndDeadlineBetween(TaskStatus status, Instant from, Instant to);
        Flux<TaskDocument> findAllByUserIdAndPriorityAndStatusOrderByDeadlineAsc(String userId, TaskPriority priority, TaskStatus status);
    Flux<TaskDocument> findAllByUserIdAndTypeAndStatusOrderByDeadlineAsc(String userId, TaskType type, TaskStatus status);
}
