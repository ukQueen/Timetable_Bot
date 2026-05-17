package com.timetablebot;

import org.springframework.boot.SpringApplication;
import com.timetablebot.infrastructure.telegram.TelegramBotProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties.class)
@EnableScheduling
public class TimetableBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimetableBotApplication.class, args);
    }
}