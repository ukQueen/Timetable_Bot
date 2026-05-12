package com.timetablebot.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin.auth")
public record AdminAuthProperties(String username, String password, String token) {
}
