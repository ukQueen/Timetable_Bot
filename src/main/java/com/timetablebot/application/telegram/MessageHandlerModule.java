package com.timetablebot.application.telegram;

import com.timetablebot.application.schedule.ScheduleModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import com.timetablebot.domain.schedule.EventType;
import com.timetablebot.domain.schedule.ScheduleEvent;
import com.timetablebot.domain.user.UserProfile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Component
public class MessageHandlerModule {
    private static final String MENU_MESSAGE = "Доступные команды:\n/start\n/menu\n/today\n/tomorrow\n/week\n/add_event title | place | start_iso | end_iso | LESSON|EXAM\n/edit_event id | title | place | start_iso | end_iso | LESSON|EXAM\n/delete_event id";
    private static final String INPUT_ERROR_MESSAGE = "Неверная команда. Используйте /menu, чтобы увидеть доступные действия.";

    private final UserModule userModule;
    private final ScheduleModule scheduleModule;

    public MessageHandlerModule(UserModule userModule, ScheduleModule scheduleModule) {
        this.userModule = userModule;
        this.scheduleModule = scheduleModule;
    }

    public Mono<BotMessageResponse> handle(TelegramUpdateRequest update) {
        if (update == null || update.message() == null || update.message().chat() == null || update.message().chat().id() == null) {
            return Mono.just(BotMessageResponse.error(INPUT_ERROR_MESSAGE));
        }

        String rawText = update.message().text() == null ? "" : update.message().text().trim();
        String[] commandSplit = rawText.split("\\s+", 2);
        String command = commandSplit[0];
        String args = commandSplit.length > 1 ? commandSplit[1] : "";
        String userId = update.message().chat().id().toString();

        return switch (command) {
            case "/start" -> userModule.createIfAbsent(userId).map(this::startResponse);
            case "/menu" -> Mono.just(BotMessageResponse.ok(MENU_MESSAGE));
            case "/today" -> userModule.createIfAbsent(userId)
                    .flatMap(profile -> scheduleModule.eventsForToday(userId, ZoneId.of(profile.timezone())).collectList())
                    .map(events -> BotMessageResponse.ok(formatEvents("Сегодня", events)));
            case "/tomorrow" -> userModule.createIfAbsent(userId)
                    .flatMap(profile -> scheduleModule.eventsForTomorrow(userId, ZoneId.of(profile.timezone())).collectList())
                    .map(events -> BotMessageResponse.ok(formatEvents("Завтра", events)));
            case "/week" -> userModule.createIfAbsent(userId)
                    .flatMap(profile -> scheduleModule.eventsForWeek(userId, ZoneId.of(profile.timezone())).collectList())
                    .map(events -> BotMessageResponse.ok(formatEvents("На 7 дней", events)));
            case "/add_event" -> createEvent(userId, args);
            case "/edit_event" -> editEvent(userId, args);
            case "/delete_event" -> deleteEvent(userId, args);
            default -> Mono.just(BotMessageResponse.error(INPUT_ERROR_MESSAGE));
        };
    }

    private Mono<BotMessageResponse> createEvent(String userId, String args) {
        try {
            String[] parts = splitParts(args, 5);
            return scheduleModule.createEvent(userId, EventType.valueOf(parts[4]), parts[0], parts[1], "manual", Instant.parse(parts[2]), Instant.parse(parts[3]))
                    .map(event -> BotMessageResponse.ok("Событие создано: " + event.title()));
        } catch (Exception ex) {
            return Mono.just(BotMessageResponse.error("Формат: /add_event title | place | start_iso | end_iso | LESSON|EXAM"));
        }
    }

    private Mono<BotMessageResponse> editEvent(String userId, String args) {
        try {
            String[] parts = splitParts(args, 6);
            return scheduleModule.updateEvent(userId, parts[0], EventType.valueOf(parts[5]), parts[1], parts[2], Instant.parse(parts[3]), Instant.parse(parts[4]))
                    .map(event -> BotMessageResponse.ok("Событие обновлено: " + event.title()))
                    .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
        } catch (Exception ex) {
            return Mono.just(BotMessageResponse.error("Формат: /edit_event id | title | place | start_iso | end_iso | LESSON|EXAM"));
        }
    }

    private Mono<BotMessageResponse> deleteEvent(String userId, String args) {
        String id = args.trim();
        if (id.isEmpty()) {
            return Mono.just(BotMessageResponse.error("Формат: /delete_event id"));
        }
        return scheduleModule.deleteEvent(userId, id)
                .thenReturn(BotMessageResponse.ok("Событие удалено."))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
    }

    private String[] splitParts(String args, int expected) {
        String[] parts = args.split("\\s*\\|\\s*");
        if (parts.length != expected) {
            throw new IllegalArgumentException("invalid");
        }
        return parts;
    }

    private String formatEvents(String period, List<ScheduleEvent> events) {
        if (events.isEmpty()) {
            return period + ": событий не найдено.";
        }
        StringBuilder builder = new StringBuilder(period + ":\n");
        for (ScheduleEvent event : events) {
            builder.append("• ").append(event.title()).append(" — ").append(event.startsAt()).append(" @ ").append(event.place()).append("\n");
        }
        return builder.toString().trim();
    }

    private BotMessageResponse startResponse(UserProfile profile) {
        String text = profile.isNewUser()
                ? "Добро пожаловать! Ваш профиль создан. Используйте /menu для списка команд."
                : "С возвращением! Используйте /menu для списка команд.";
        return BotMessageResponse.ok(text);
    }
}
