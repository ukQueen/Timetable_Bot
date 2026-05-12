package com.timetablebot.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class AdminAuthConfig {
}
