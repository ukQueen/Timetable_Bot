package com.timetablebot.infrastructure.security;

import com.timetablebot.application.security.AccessPolicy;
import com.timetablebot.domain.security.Role;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AdminAuthWebFilter implements WebFilter {

    private static final String ADMIN_PREFIX = "/admin";
    private static final String AUTH_PATH = "/auth";

    private final AdminAuthProperties properties;

    public AdminAuthWebFilter(AdminAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith(ADMIN_PREFIX) || path.equals(AUTH_PATH) || path.equals("/healthcheck")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String expected = "Bearer " + properties.token();

        if (expected.equals(authHeader)) {
            exchange.getAttributes().put(AccessPolicy.ROLE_ATTRIBUTE, Role.ADMIN);
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
