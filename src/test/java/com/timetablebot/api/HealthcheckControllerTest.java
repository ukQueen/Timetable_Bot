package com.timetablebot.api;

import com.timetablebot.TimetableBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

@org.junit.jupiter.api.Disabled("Requires rewrite to plain Spring test bootstrap")
class HealthcheckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnHealthcheckStatus() {
        webTestClient.get()
                .uri("/healthcheck")
                .header("X-Request-Id", "req-health-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "req-health-1")
                .expectBody()
                .jsonPath("$.status").value(status -> {
                    String value = String.valueOf(status);
                    if (!List.of("UP", "DEGRADED", "DOWN").contains(value)) {
                        throw new AssertionError("Unexpected status: " + value);
                    }
                })
                .jsonPath("$.service").isEqualTo("timetable-bot")
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.request_id").isEqualTo("req-health-1")
                .jsonPath("$.dependencies.mongodb").exists()
                .jsonPath("$.dependencies.rabbitmq").exists()
                .jsonPath("$.dependencies.telegram").exists();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() {
        webTestClient.get()
                .uri("/healthcheck")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.request_id").exists();
    }
}
