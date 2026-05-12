package com.timetablebot.api.admin;

import com.timetablebot.application.security.AccessPolicy;
import com.timetablebot.domain.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class AdminWhoAmIController {

    private final AccessPolicy accessPolicy;

    public AdminWhoAmIController(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/admin/whoami")
    public Mono<Map<String, String>> whoami(ServerWebExchange exchange) {
        accessPolicy.requireRole(exchange, Role.ADMIN);
        return Mono.just(Map.of("role", Role.ADMIN.name()));
    }
}
