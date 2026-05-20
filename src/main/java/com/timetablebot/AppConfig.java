package com.timetablebot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@Configuration
@ComponentScan(basePackages = "com.timetablebot")
@EnableScheduling
@EnableReactiveMongoRepositories(basePackages = "com.timetablebot.infrastructure")
public class AppConfig {
}
