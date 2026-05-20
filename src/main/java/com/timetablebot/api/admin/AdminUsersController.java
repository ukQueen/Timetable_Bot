package com.timetablebot.api.admin;

import com.timetablebot.application.security.AccessPolicy;
import com.timetablebot.domain.security.Role;
import com.timetablebot.infrastructure.user.UserDocument;
import com.timetablebot.infrastructure.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.time.Instant;

@RestController
public class AdminUsersController {

    private final AccessPolicy accessPolicy;
    private final UserRepository userRepository;

    public AdminUsersController(AccessPolicy accessPolicy, UserRepository userRepository) {
        this.accessPolicy = accessPolicy;
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/users")
    public Flux<AdminUserResponse> users(ServerWebExchange exchange) {
        accessPolicy.requireRole(exchange, Role.ADMIN);
        return userRepository.findAll().map(this::toResponse);
    }

    private AdminUserResponse toResponse(UserDocument doc) {
        return new AdminUserResponse(
                doc.getId(),
                doc.getTimezone(),
                doc.getOnboardingStatus() == null ? null : doc.getOnboardingStatus().name(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public record AdminUserResponse(String id,
                                    String timezone,
                                    String onboardingStatus,
                                    Instant createdAt,
                                    Instant updatedAt) {
    }
}
