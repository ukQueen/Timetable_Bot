package com.timetablebot;

import org.springframework.boot.SpringApplication;
import com.timetablebot.infrastructure.telegram.TelegramBotProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TimetableBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimetableBotApplication.class, args);
    }
}