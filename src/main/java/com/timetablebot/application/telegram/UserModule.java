package com.timetablebot.application.telegram;

import com.timetablebot.domain.user.OnboardingStatus;
import com.timetablebot.domain.user.UserProfile;
import com.timetablebot.domain.user.WeekStartPreference;
import com.timetablebot.infrastructure.user.UserDocument;
import com.timetablebot.infrastructure.user.UserRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class UserModule {
    private final UserRepository userRepository;

    public UserModule(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<UserProfile> createIfAbsent(String userId) {
        return userRepository.findById(userId)
                .map(doc -> toProfile(doc, false))
                .switchIfEmpty(createNew(userId));
    }

    public Mono<UserProfile> setTimezone(String userId, String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Неверный часовой пояс: " + timezone));
        }
        return userRepository.findById(userId)
                .switchIfEmpty(createNew(userId).flatMap(p -> userRepository.findById(userId)))
                .flatMap(doc -> {
                    doc.setTimezone(timezone);
                    doc.setUpdatedAt(Instant.now());
                    return userRepository.save(doc);
                })
                .map(doc -> toProfile(doc, false));
    }

    private Mono<UserProfile> createNew(String userId) {
        Instant now = Instant.now();
        UserDocument doc = new UserDocument();
        doc.setId(userId);
        doc.setTimezone("Europe/Moscow");         doc.setWeekStartPreference(WeekStartPreference.MONDAY);
        doc.setOnboardingStatus(OnboardingStatus.NEW);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return userRepository.save(doc)
                .map(saved -> toProfile(saved, true));
    }

    private UserProfile toProfile(UserDocument doc, boolean isNewUser) {
        return new UserProfile(
                doc.getId(),
                doc.getTimezone() != null ? doc.getTimezone() : "Europe/Moscow",
                doc.getWeekStartPreference(),
                doc.getOnboardingStatus(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                isNewUser
        );
    }
}
