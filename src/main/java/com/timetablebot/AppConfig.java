package com.timetablebot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.web.reactive.config.EnableWebFlux;

@Configuration
@ComponentScan(basePackages = "com.timetablebot")
@EnableScheduling
@EnableWebFlux
@EnableRabbit
@EnableReactiveMongoRepositories(basePackages = "com.timetablebot.infrastructure")
public class AppConfig {
}
