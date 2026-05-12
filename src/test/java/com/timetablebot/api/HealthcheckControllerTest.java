package com.timetablebot.api;

import com.timetablebot.TimetableBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

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
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("timetable-bot")
                .jsonPath("$.timestamp").exists();
    }
}
