package com.timetablebot.application.telegram;

import com.timetablebot.application.schedule.ScheduleModule;
import com.timetablebot.application.telegram.dto.BotMessageResponse;
import com.timetablebot.application.telegram.dto.TelegramUpdateRequest;
import com.timetablebot.application.task.TaskModule;

import com.timetablebot.domain.schedule.EventType;
import com.timetablebot.domain.schedule.ImportHistoryItem;
import com.timetablebot.domain.schedule.ScheduleEvent;
import com.timetablebot.domain.user.*;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MessageHandlerModule {
    private static final DateTimeFormatter IMPORT_HISTORY_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String MENU_MESSAGE = """
Доступные команды:
/start
/menu
/today
/tomorrow
/week
/add_event title | place | start_iso | end_iso | LESSON|EXAM
/edit_event id | title | place | start_iso | end_iso | LESSON|EXAM
/delete_event id
/import_timetable <CSV или iCal>
/import_external <url_csv>
/imports
/add_task title | deadline_iso | LOW|MEDIUM|HIGH | HOMEWORK|LAB|COURSEWORK|OTHER
/edit_task id | title | deadline_iso | LOW|MEDIUM|HIGH | HOMEWORK|LAB|COURSEWORK|OTHER
/tasks_today
/tasks_week
/tasks_overdue
/done_task id
/delete_task id
""";
    private static final String INPUT_ERROR_MESSAGE = "Неверная команда. Используйте /menu, чтобы увидеть доступные действия.";

    private final UserModule userModule;
    private final ScheduleModule scheduleModule;
    private final TaskModule taskModule;

    public MessageHandlerModule(UserModule userModule, ScheduleModule scheduleModule, TaskModule taskModule) {
        this.userModule = userModule;
        this.scheduleModule = scheduleModule;
        this.taskModule = taskModule;
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
            case "/import_timetable" -> importTimetable(userId, args);
            case "/import_external" -> importExternal(userId, args);
            case "/imports" -> scheduleModule.importHistory(userId).collectList().map(this::formatImportHistory);
            case "/add_task" -> addTask(userId, args);
            case "/tasks_today" -> userModule.createIfAbsent(userId).flatMap(profile -> taskModule.tasksForToday(userId, ZoneId.of(profile.timezone())).collectList()).map(this::formatTasks);
            case "/tasks_week" -> userModule.createIfAbsent(userId).flatMap(profile -> taskModule.tasksForWeek(userId, ZoneId.of(profile.timezone())).collectList()).map(this::formatTasks);
            case "/done_task" -> doneTask(userId, args);
            case "/delete_task" -> deleteTask(userId, args);
            case "/edit_task" -> editTask(userId, args);
            case "/tasks_overdue" -> userModule.createIfAbsent(userId).flatMap(profile -> taskModule.overdueTasks(userId, ZoneId.of(profile.timezone())).collectList()).map(tasks -> formatTasks("Просроченные задачи", tasks));
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

    private Mono<BotMessageResponse> addTask(String userId, String args) {
        try {
            String[] parts = splitPipe(args, 4);
            return taskModule.createTask(userId, parts[0], Instant.parse(parts[1]), parseTaskPriority(parts[2]), parseTaskType(parts[3]))
                    .map(task -> BotMessageResponse.ok("Задача создана: " + task.title()));
        } catch (Exception ex) {
            return Mono.just(BotMessageResponse.error("Формат: /add_task title | deadline_iso | LOW|MEDIUM|HIGH | HOMEWORK|LAB|COURSEWORK|OTHER"));
        }
    }



    private Mono<BotMessageResponse> editTask(String userId, String args) {
        try {
            String[] parts = splitPipe(args, 5);
            return taskModule.updateTask(userId, parts[0], parts[1], Instant.parse(parts[2]), parseTaskPriority(parts[3]), parseTaskType(parts[4]))
                    .map(task -> BotMessageResponse.ok("Задача обновлена: " + task.title()))
                    .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
        } catch (Exception ex) {
            return Mono.just(BotMessageResponse.error("Формат: /edit_task id | title | deadline_iso | LOW|MEDIUM|HIGH | HOMEWORK|LAB|COURSEWORK|OTHER"));
        }
    }

    private Mono<BotMessageResponse> doneTask(String userId, String args) {
        String id = args.trim();
        if (id.isEmpty()) return Mono.just(BotMessageResponse.error("Формат: /done_task id"));
        return taskModule.markDone(userId, id).map(task -> BotMessageResponse.ok("Задача отмечена выполненной: " + task.title()))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
    }

    private Mono<BotMessageResponse> deleteTask(String userId, String args) {
        String id = args.trim();
        if (id.isEmpty()) return Mono.just(BotMessageResponse.error("Формат: /delete_task id"));
        return taskModule.deleteTask(userId, id).thenReturn(BotMessageResponse.ok("Задача удалена."))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
    }

    private BotMessageResponse formatTasks(List<TaskItem> tasks) {
        return formatTasks("Задачи", tasks);
    }

    private BotMessageResponse formatTasks(String header, List<TaskItem> tasks) {
        if (tasks.isEmpty()) {
            return BotMessageResponse.ok(header + ": не найдено.");
        }
        StringBuilder b = new StringBuilder(header + ":\n");
        for (TaskItem t : tasks) {
            b.append("• ").append(t.id()).append(" | ").append(t.title()).append(" | ").append(t.priority()).append(" | ").append(t.status()).append(" | ").append(t.deadline()).append("\n");
        }
        return BotMessageResponse.ok(b.toString().trim());
    }



    private TaskPriority parseTaskPriority(String raw) {
        return TaskPriority.valueOf(raw.trim().toUpperCase());
    }

    private TaskType parseTaskType(String raw) {
        return TaskType.valueOf(raw.trim().toUpperCase());
    }

    private String[] splitPipe(String args, int expected) {
        String[] parts = args.split("\\s*\\|\\s*");
        if (parts.length != expected) throw new IllegalArgumentException("invalid");
        return parts;
    }

    private Mono<BotMessageResponse> importExternal(String userId, String args) {
        String url = args.trim();
        if (url.isEmpty()) {
            return Mono.just(BotMessageResponse.error("Формат: /import_external <url_csv>"));
        }

        return scheduleModule.importFromExternalApi(userId, url)
                .map(count -> BotMessageResponse.ok("Импорт из API завершен. Загружено событий: " + count))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
    }

    private Mono<BotMessageResponse> importTimetable(String userId, String args) {
        String payload = args.trim();
        if (payload.isEmpty()) {
            return Mono.just(BotMessageResponse.error("Формат: /import_timetable <CSV или iCal>"));
        }

        Mono<Integer> importResult = payload.startsWith("BEGIN:VCALENDAR")
                ? scheduleModule.importFromIcal(userId, payload)
                : scheduleModule.importFromCsv(userId, payload);

        return importResult
                .map(count -> BotMessageResponse.ok("Импорт завершен. Загружено событий: " + count))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(ex.getMessage())));
    }

    private BotMessageResponse formatImportHistory(List<ImportHistoryItem> imports) {
        if (imports.isEmpty()) {
            return BotMessageResponse.ok("Импорты: история пуста.");
        }
        StringBuilder b = new StringBuilder("Последние импорты:\n");
        for (ImportHistoryItem item : imports) {
            b.append("• ")
                    .append(formatImportCreatedAt(item.createdAt()))
                    .append(" | ")
                    .append(item.source())
                    .append(" | ")
                    .append(localizeImportStatus(item.status()))
                    .append(" | imported=")
                    .append(item.importedCount());
            if (item.errorMessage() != null && !item.errorMessage().isBlank()) {
                b.append(" | error=").append(truncate(item.errorMessage(), 160));
            }
            b.append("\n");
        }
        return BotMessageResponse.ok(b.toString().trim());
    }

    private String localizeImportStatus(com.timetablebot.domain.schedule.ImportStatus status) {
        return switch (status) {
            case SUCCESS -> "УСПЕХ";
            case PARTIAL -> "ЧАСТИЧНО";
            case ERROR -> "ОШИБКА";
        };
    }

    private String formatImportCreatedAt(java.time.Instant createdAt) {
        return createdAt == null ? "unknown_time" : IMPORT_HISTORY_TIME_FORMATTER.format(createdAt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
