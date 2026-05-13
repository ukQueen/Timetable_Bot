package com.timetablebot.infrastructure.user;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRepository extends ReactiveCrudRepository<UserDocument, String> {
}
