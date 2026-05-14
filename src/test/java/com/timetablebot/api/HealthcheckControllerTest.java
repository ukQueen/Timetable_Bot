package com.timetablebot.api;

import com.timetablebot.TimetableBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

@SpringBootTest(classes = TimetableBotApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HealthcheckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnHealthcheckStatus() {
        webTestClient.get()
                .uri("/healthcheck")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").value(status -> {
                    String value = String.valueOf(status);
                    if (!List.of("UP", "DEGRADED", "DOWN").contains(value)) {
                        throw new AssertionError("Unexpected status: " + value);
                    }
                })
                .jsonPath("$.service").isEqualTo("timetable-bot")
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.dependencies.mongodb").exists()
                .jsonPath("$.dependencies.rabbitmq").exists();
    }
}
