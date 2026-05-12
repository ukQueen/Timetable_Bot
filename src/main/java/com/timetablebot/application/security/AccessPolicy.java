package com.timetablebot.application.security;

import com.timetablebot.domain.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@Component
public class AccessPolicy {

    public static final String ROLE_ATTRIBUTE = "current_role";

    public Role currentRole(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(ROLE_ATTRIBUTE);
        return value instanceof Role role ? role : Role.USER;
    }

    public void requireRole(ServerWebExchange exchange, Role requiredRole) {
        Role actual = currentRole(exchange);
        if (actual != requiredRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden for role: " + actual);
        }
    }
}
