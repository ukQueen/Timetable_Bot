package com.timetablebot.api.admin;

import com.timetablebot.infrastructure.security.AdminAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class AdminAuthController {

    private final AdminAuthProperties properties;

    public AdminAuthController(AdminAuthProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/auth")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, String>> auth(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (properties.username().equals(username) && properties.password().equals(password)) {
            return Mono.just(Map.of("token", properties.token(), "type", "Bearer"));
        }

        return Mono.error(new UnauthorizedException());
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException {
    }
}
