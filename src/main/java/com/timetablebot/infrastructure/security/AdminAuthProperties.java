package com.timetablebot.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AdminAuthProperties(
        @Value("${admin.auth.username:admin}") String username,
        @Value("${admin.auth.password:admin123}") String password,
        @Value("${admin.auth.token:dev-admin-token}") String token
) {
}
