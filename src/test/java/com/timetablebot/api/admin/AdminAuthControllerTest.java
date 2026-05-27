package com.timetablebot.api.admin;

import com.timetablebot.TimetableBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

@org.junit.jupiter.api.Disabled("Requires rewrite to plain Spring test bootstrap")
class AdminAuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnTokenForValidCredentials() {
        webTestClient.post()
                .uri("/admin/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "admin123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("Bearer")
                .jsonPath("$.token").isEqualTo("dev-admin-token");
    }

    @Test
    void shouldRejectInvalidCredentials() {
        webTestClient.post()
                .uri("/admin/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectAdminEndpointWithoutToken() {
        webTestClient.get()
                .uri("/admin/ping")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAdminEndpointWithToken() {
        webTestClient.get()
                .uri("/admin/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer dev-admin-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void shouldReturnAdminRoleForWhoAmI() {
        webTestClient.get()
                .uri("/admin/whoami")
                .header(HttpHeaders.AUTHORIZATION, "Bearer dev-admin-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

}
