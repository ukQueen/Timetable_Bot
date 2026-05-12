package com.timetablebot.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthcheckController {

    @GetMapping(value = "/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> healthcheck() {
        return Mono.just(Map.of(
                "status", "UP",
                "service", "timetable-bot",
                "timestamp", Instant.now().toString()
        ));
    }
}
