package com.timetablebot.application.telegram;

import com.timetablebot.application.llm.LlmAdvisorModule;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageHandlerModule {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, DialogState> dialogStates = new ConcurrentHashMap<>();

    private static final String MENU_MESSAGE = """
📋 Доступные команды:

📅 Расписание:
/today — занятия на сегодня
/tomorrow — занятия на завтра
/week — занятия на 7 дней
/all_events — все события
/add_event — добавить событие
/edit_event — редактировать событие
/delete_event — удалить событие
/import_timetable — импорт из CSV (текст или файл)
/imports — история импортов

📝 Задачи:
/all_tasks — все открытые задачи
/tasks_today — задачи на сегодня
/tasks_tomorrow — задачи на завтра
/tasks_week — задачи на неделю
/tasks_overdue — просроченные задачи
/task_priority high|medium|low — по приоритету
/task_type homework|lab|coursework|other — по типу
/add_task — добавить задачу
/edit_task — редактировать задачу
/done_task — отметить выполненной
/delete_task — удалить задачу

🤖 LLM-советник:
/ask <вопрос> — задать вопрос советнику
/priority — совет по приоритетам
/study_plan <предмет> | <дата> — план подготовки

⚙️ Настройки:
/set_timezone Europe/Moscow — установить часовой пояс
/my_timezone — текущий часовой пояс

ℹ️ /help — примеры команд
""";

    private static final String HELP_MESSAGE = """
📖 Примеры команд:

📅 Расписание:
/today — показать занятия на сегодня
/week — показать занятия на 7 дней
/add_event — бот спросит название, место, время по шагам
/edit_event — покажет список, введи номер
/delete_event — покажет список, введи номер
/import_timetable — выбери: текст CSV или файл .csv
Формат CSV строки:
  LESSON,Математика,Ауд 305,2026-06-01T10:00:00Z,2026-06-01T11:30:00Z
  EXAM,Физика,Ауд 410,2026-06-05T09:00:00Z,2026-06-05T12:00:00Z

📝 Задачи:
/add_task — бот спросит название, дедлайн, приоритет по шагам
/tasks_today — задачи на сегодня
/task_priority high — только задачи с высоким приоритетом
/task_type lab — только лабораторные работы
/done_task — покажет список, введи номер
/delete_task — покажет список, введи номер

🤖 LLM-советник:
/ask Как лучше подготовиться к экзамену за неделю?
/study_plan Математика | 2026-06-10T09:00

⏰ Время вводите в вашем часовом поясе: 2026-06-01T23:59
   (без буквы Z на конце)
""";

    private static final String INPUT_ERROR_MESSAGE = "Неверная команда. Используйте /menu.";

    private final UserModule userModule;
    private final ScheduleModule scheduleModule;
    private final TaskModule taskModule;
    private final LlmAdvisorModule llmAdvisorModule;

    public MessageHandlerModule(UserModule userModule, ScheduleModule scheduleModule,
                                 TaskModule taskModule, LlmAdvisorModule llmAdvisorModule) {
        this.userModule = userModule;
        this.scheduleModule = scheduleModule;
        this.taskModule = taskModule;
        this.llmAdvisorModule = llmAdvisorModule;
    }

    public Mono<BotMessageResponse> handle(TelegramUpdateRequest update) {
        if (update == null || update.message() == null || update.message().chat() == null
                || update.message().chat().id() == null) {
            return Mono.just(BotMessageResponse.error(INPUT_ERROR_MESSAGE));
        }

        String userId = update.message().chat().id().toString();

                if (update.message().document() != null) {
            DialogState state = dialogStates.get(userId);
            if (state != null && state.type() == DialogType.IMPORT_TIMETABLE_FILE) {
                return handleImportFile(userId, update.message().document());
            }
            return Mono.just(BotMessageResponse.error("Для импорта файла сначала введите /import_timetable и выберите вариант."));
        }

        String rawText = update.message().text() == null ? "" : update.message().text().trim();

                DialogState state = dialogStates.get(userId);
        if (state != null && !rawText.startsWith("/")) {
            return handleDialogStep(userId, rawText, state);
        }

        String[] commandSplit = rawText.split("\\s+", 2);
        String command = commandSplit[0].split("@")[0].toLowerCase();
        String args = commandSplit.length > 1 ? commandSplit[1] : "";

        return switch (command) {
            case "/start" -> userModule.createIfAbsent(userId).map(this::startResponse);
            case "/menu" -> Mono.just(BotMessageResponse.ok(MENU_MESSAGE));
            case "/help" -> Mono.just(BotMessageResponse.ok(HELP_MESSAGE));

            case "/today" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> scheduleModule.eventsForToday(userId, ZoneId.of(p.timezone())).collectList()
                    .map(events -> BotMessageResponse.ok(formatEvents("Сегодня", events, ZoneId.of(p.timezone())))));
            case "/tomorrow" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> scheduleModule.eventsForTomorrow(userId, ZoneId.of(p.timezone())).collectList()
                    .map(events -> BotMessageResponse.ok(formatEvents("Завтра", events, ZoneId.of(p.timezone())))));
            case "/week" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> scheduleModule.eventsForWeek(userId, ZoneId.of(p.timezone())).collectList()
                    .map(events -> BotMessageResponse.ok(formatEvents("На 7 дней", events, ZoneId.of(p.timezone())))));
            case "/all_events" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> scheduleModule.allEvents(userId).collectList()
                    .map(events -> BotMessageResponse.ok(formatEvents("Все события", events, ZoneId.of(p.timezone())))));
            case "/add_event" -> startAddEventDialog(userId);
            case "/edit_event" -> startEditEventDialog(userId);
            case "/delete_event" -> startDeleteEventDialog(userId);
            case "/import_timetable" -> startImportDialog(userId);
            case "/imports" -> scheduleModule.importHistory(userId).collectList().map(this::formatImportHistory);

            case "/all_tasks" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> taskModule.allOpenTasks(userId).collectList()
                    .map(tasks -> formatAllTasks(tasks, ZoneId.of(p.timezone()))));
            case "/tasks_today" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> taskModule.tasksForToday(userId, ZoneId.of(p.timezone())).collectList()
                    .map(tasks -> formatTasks("Задачи на сегодня", tasks, ZoneId.of(p.timezone()))));
            case "/tasks_tomorrow" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> taskModule.tasksForTomorrow(userId, ZoneId.of(p.timezone())).collectList()
                    .map(tasks -> formatTasks("Задачи на завтра", tasks, ZoneId.of(p.timezone()))));
            case "/tasks_week" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> taskModule.tasksForWeek(userId, ZoneId.of(p.timezone())).collectList()
                    .map(tasks -> formatTasks("Задачи на неделю", tasks, ZoneId.of(p.timezone()))));
            case "/tasks_overdue" -> userModule.createIfAbsent(userId)
                    .flatMap(p -> taskModule.overdueTasks(userId, ZoneId.of(p.timezone())).collectList()
                    .map(tasks -> formatTasks("⚠️ Просроченные задачи", tasks, ZoneId.of(p.timezone()))));
            case "/task_priority" -> handleTaskPriority(userId, args);
            case "/task_type" -> handleTaskType(userId, args);
            case "/add_task" -> startAddTaskDialog(userId);
            case "/edit_task" -> startEditTaskDialog(userId);
            case "/done_task" -> startDoneTaskDialog(userId);
            case "/delete_task" -> startDeleteTaskDialog(userId);

            case "/set_timezone" -> setTimezone(userId, args);
            case "/my_timezone" -> myTimezone(userId);
            case "/ask" -> askLlm(args);
            case "/priority" -> advisePriority(userId);
            case "/study_plan" -> studyPlan(args);

            default -> Mono.just(BotMessageResponse.error(INPUT_ERROR_MESSAGE));
        };
    }
    private Mono<BotMessageResponse> handleDialogStep(String userId, String text, DialogState state) {
        return switch (state.type()) {
            case ADD_EVENT -> handleAddEventStep(userId, text, state);
            case EDIT_EVENT_SELECT -> handleEditEventSelect(userId, text, state);
            case EDIT_EVENT_FIELD -> handleEditEventField(userId, text, state);
            case DELETE_EVENT -> handleDeleteEventSelect(userId, text, state);
            case IMPORT_TIMETABLE_CHOOSE -> handleImportChoose(userId, text);
            case IMPORT_TIMETABLE_TEXT -> handleImportText(userId, text);
            case IMPORT_TIMETABLE_FILE -> Mono.just(BotMessageResponse.ok("Отправьте файл .csv для импорта."));
            case ADD_TASK -> handleAddTaskStep(userId, text, state);
            case EDIT_TASK_SELECT -> handleEditTaskSelect(userId, text, state);
            case EDIT_TASK_FIELD -> handleEditTaskField(userId, text, state);
            case DONE_TASK -> handleDoneTaskSelect(userId, text, state);
            case DELETE_TASK -> handleDeleteTaskSelect(userId, text, state);
        };
    }
    private Mono<BotMessageResponse> startAddEventDialog(String userId) {
        return userModule.createIfAbsent(userId).map(profile -> {
            String[] data = new String[6];
            data[5] = profile.timezone();             dialogStates.put(userId, new DialogState(DialogType.ADD_EVENT, 0, data));
            return BotMessageResponse.ok("📝 Добавление события\n\nШаг 1/5: Введите название события:");
        });
    }

    private Mono<BotMessageResponse> handleAddEventStep(String userId, String text, DialogState state) {
        String[] data = state.data();
        int step = state.step();
        data[step] = text.trim();

        return switch (step) {
            case 0 -> {
                dialogStates.put(userId, new DialogState(DialogType.ADD_EVENT, 1, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 2/5: Введите место (аудитория):"));
            }
            case 1 -> {
                dialogStates.put(userId, new DialogState(DialogType.ADD_EVENT, 2, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 3/5: Введите дату и время начала в вашем часовом поясе:\nПример: 2026-06-01T10:00"));
            }
            case 2 -> {
                                try {
                    ZoneId userZone = ZoneId.of(data[5] != null ? data[5] : "Europe/Moscow");
                    Instant start = parseUserDateTime(text.trim(), userZone);
                    if (start.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Нельзя указать время в прошлом. Введите дату начала заново:"));
                    }
                    data[2] = start.toString();                     dialogStates.put(userId, new DialogState(DialogType.ADD_EVENT, 3, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 4/5: Введите дату и время окончания в вашем часовом поясе:\nПример: 2026-06-01T11:30"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат даты. Пример: 2026-06-01T10:00\nПовторите ввод:"));
                }
            }
            case 3 -> {
                                try {
                    ZoneId userZone = ZoneId.of(data[5] != null ? data[5] : "Europe/Moscow");
                    Instant end = parseUserDateTime(text.trim(), userZone);
                    Instant start = Instant.parse(data[2]);
                    if (end.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Нельзя указать время в прошлом. Введите дату окончания заново:"));
                    }
                    if (!end.isAfter(start)) {
                        yield Mono.just(BotMessageResponse.error("❌ Время окончания должно быть позже времени начала (" +
                                formatDateTime(start, userZone) + ").\nВведите дату окончания заново:"));
                    }
                    data[3] = end.toString();
                    dialogStates.put(userId, new DialogState(DialogType.ADD_EVENT, 4, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 5/5: Выберите тип события:\n1 — LESSON (занятие)\n2 — EXAM (экзамен)"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат даты. Пример: 2026-06-01T11:30\nПовторите ввод:"));
                }
            }
            case 4 -> {
                EventType type = switch (text.trim().toLowerCase()) {
                    case "1", "lesson" -> EventType.LESSON;
                    case "2", "exam" -> EventType.EXAM;
                    default -> null;
                };
                if (type == null) {
                    yield Mono.just(BotMessageResponse.error("Введите 1 (LESSON) или 2 (EXAM):"));
                }
                dialogStates.remove(userId);
                yield scheduleModule.createEvent(userId, type, data[0], data[1], "manual",
                        Instant.parse(data[2]), Instant.parse(data[3]))
                        .map(event -> BotMessageResponse.ok("✅ Событие добавлено: " + event.title() +
                                "\n📅 " + formatDateTime(event.startsAt(), ZoneId.of("Europe/Moscow")) +
                                " — " + event.endsAt().atZone(ZoneId.of("Europe/Moscow")).format(TIME_FMT) +
                                " @ " + event.place()))
                        .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
            }
            default -> {
                dialogStates.remove(userId);
                yield Mono.just(BotMessageResponse.error("Ошибка диалога. Начните заново."));
            }
        };
    }
    private Mono<BotMessageResponse> startEditEventDialog(String userId) {
        return scheduleModule.allEvents(userId).collectList().map(events -> {
            if (events.isEmpty()) return BotMessageResponse.ok("У вас нет событий для редактирования.");
            String[] ids = events.stream().map(ScheduleEvent::id).toArray(String[]::new);
            dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_SELECT, 0, ids));
            return BotMessageResponse.ok("✏️ Выберите событие (введите номер):\n\n" + formatEventsNumbered(events));
        });
    }

    private Mono<BotMessageResponse> handleEditEventSelect(String userId, String text, DialogState state) {
        try {
            int num = Integer.parseInt(text.trim()) - 1;
            if (num < 0 || num >= state.data().length)
                return Mono.just(BotMessageResponse.error("Неверный номер. Введите от 1 до " + state.data().length));
            String eventId = state.data()[num];
            dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_FIELD, 0, new String[]{eventId, "", "", "", "", ""}));
            return Mono.just(BotMessageResponse.ok("Шаг 1/5: Введите новое название события:"));
        } catch (NumberFormatException e) {
            return Mono.just(BotMessageResponse.error("Введите номер события:"));
        }
    }

    private Mono<BotMessageResponse> handleEditEventField(String userId, String text, DialogState state) {
        String[] data = state.data();
        int step = state.step();
        data[step + 1] = text.trim();

        return switch (step) {
            case 0 -> {
                dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_FIELD, 1, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 2/5: Введите новое место:"));
            }
            case 1 -> {
                dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_FIELD, 2, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 3/5: Введите новую дату и время начала в вашем поясе:\nПример: 2026-06-01T10:00"));
            }
            case 2 -> {
                try {
                    Instant start = Instant.parse(text.trim());
                    if (start.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Нельзя указать время в прошлом. Введите дату начала заново:"));
                    }
                    dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_FIELD, 3, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 4/5: Введите новую дату и время окончания в вашем поясе:\nПример: 2026-06-01T11:30"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат. Пример: 2026-06-01T10:00\nПовторите:"));
                }
            }
            case 3 -> {
                try {
                    Instant end = Instant.parse(text.trim());
                    Instant start = Instant.parse(data[3]);
                    if (end.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Нельзя указать время в прошлом. Введите дату окончания заново:"));
                    }
                    if (!end.isAfter(start)) {
                        yield Mono.just(BotMessageResponse.error("❌ Время окончания должно быть позже начала (" +
                                formatDateTime(start, ZoneId.of("UTC")) + ").\nВведите дату окончания заново:"));
                    }
                    dialogStates.put(userId, new DialogState(DialogType.EDIT_EVENT_FIELD, 4, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 5/5: Тип события:\n1 — LESSON\n2 — EXAM"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат. Пример: 2026-06-01T11:30\nПовторите:"));
                }
            }
            case 4 -> {
                EventType type = switch (text.trim().toLowerCase()) {
                    case "1", "lesson" -> EventType.LESSON;
                    case "2", "exam" -> EventType.EXAM;
                    default -> null;
                };
                if (type == null) yield Mono.just(BotMessageResponse.error("Введите 1 или 2:"));
                dialogStates.remove(userId);
                yield scheduleModule.updateEvent(userId, data[0], type, data[1], data[2],
                        Instant.parse(data[3]), Instant.parse(data[4]))
                        .map(event -> BotMessageResponse.ok("✅ Событие обновлено: " + event.title()))
                        .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
            }
            default -> {
                dialogStates.remove(userId);
                yield Mono.just(BotMessageResponse.error("Ошибка диалога."));
            }
        };
    }
    private Mono<BotMessageResponse> startDeleteEventDialog(String userId) {
        return scheduleModule.allEvents(userId).collectList().map(events -> {
            if (events.isEmpty()) return BotMessageResponse.ok("У вас нет событий для удаления.");
            String[] ids = events.stream().map(ScheduleEvent::id).toArray(String[]::new);
            dialogStates.put(userId, new DialogState(DialogType.DELETE_EVENT, 0, ids));
            return BotMessageResponse.ok("🗑 Выберите событие для удаления (введите номер):\n\n" + formatEventsNumbered(events));
        });
    }

    private Mono<BotMessageResponse> handleDeleteEventSelect(String userId, String text, DialogState state) {
        try {
            int num = Integer.parseInt(text.trim()) - 1;
            if (num < 0 || num >= state.data().length)
                return Mono.just(BotMessageResponse.error("Неверный номер. Введите от 1 до " + state.data().length));
            String eventId = state.data()[num];
            dialogStates.remove(userId);
            return scheduleModule.deleteEvent(userId, eventId)
                    .thenReturn(BotMessageResponse.ok("✅ Событие удалено."))
                    .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
        } catch (NumberFormatException e) {
            return Mono.just(BotMessageResponse.error("Введите номер события:"));
        }
    }
    private Mono<BotMessageResponse> startImportDialog(String userId) {
        dialogStates.put(userId, new DialogState(DialogType.IMPORT_TIMETABLE_CHOOSE, 0, new String[0]));
        return Mono.just(BotMessageResponse.ok("""
📂 Выберите способ импорта:
1 — Вставить CSV текстом
2 — Отправить файл .csv"""));
    }

    private Mono<BotMessageResponse> handleImportChoose(String userId, String text) {
        return switch (text.trim()) {
            case "1" -> {
                dialogStates.put(userId, new DialogState(DialogType.IMPORT_TIMETABLE_TEXT, 0, new String[0]));
                yield Mono.just(BotMessageResponse.ok("""
Отправьте CSV текст. Формат строк:
LESSON,Название,Место,2026-06-01T10:00:00Z,2026-06-01T11:30:00Z
EXAM,Название,Место,2026-06-05T09:00:00Z,2026-06-05T12:00:00Z

Тип: LESSON или EXAM (регистр не важен)"""));
            }
            case "2" -> {
                dialogStates.put(userId, new DialogState(DialogType.IMPORT_TIMETABLE_FILE, 0, new String[0]));
                yield Mono.just(BotMessageResponse.ok("📎 Отправьте файл в формате .csv"));
            }
            default -> Mono.just(BotMessageResponse.error("Введите 1 (текст) или 2 (файл):"));
        };
    }

    private Mono<BotMessageResponse> handleImportText(String userId, String text) {
        dialogStates.remove(userId);
        return scheduleModule.importFromCsv(userId, text)
                .map(count -> BotMessageResponse.ok("✅ Импорт завершён. Загружено событий: " + count))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка импорта: " + ex.getMessage())));
    }

    private Mono<BotMessageResponse> handleImportFile(String userId, TelegramUpdateRequest.Document document) {
        dialogStates.remove(userId);
        if (document.fileName() == null || !document.fileName().toLowerCase().endsWith(".csv")) {
            return Mono.just(BotMessageResponse.error("❌ Нужен файл формата .csv"));
        }
        return scheduleModule.importFromTelegramFile(userId, document.fileId())
                .map(count -> BotMessageResponse.ok("✅ Импорт из файла завершён. Загружено событий: " + count))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка импорта файла: " + ex.getMessage())));
    }
    private Mono<BotMessageResponse> startAddTaskDialog(String userId) {
        return userModule.createIfAbsent(userId).map(profile -> {
            String[] data = new String[5];
            data[4] = profile.timezone();
            dialogStates.put(userId, new DialogState(DialogType.ADD_TASK, 0, data));
            return BotMessageResponse.ok("📝 Добавление задачи\n\nШаг 1/4: Введите название задачи:");
        });
    }

    private Mono<BotMessageResponse> handleAddTaskStep(String userId, String text, DialogState state) {
        String[] data = state.data();
        int step = state.step();
        data[step] = text.trim();

        return switch (step) {
            case 0 -> {
                dialogStates.put(userId, new DialogState(DialogType.ADD_TASK, 1, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 2/4: Введите дедлайн в вашем часовом поясе:\nПример: 2026-06-01T23:59"));
            }
            case 1 -> {
                try {
                    ZoneId userZone = ZoneId.of(data[4] != null ? data[4] : "Europe/Moscow");
                    Instant deadline = parseUserDateTime(text.trim(), userZone);
                    if (deadline.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Дедлайн не может быть в прошлом. Введите дедлайн заново:"));
                    }
                    data[1] = deadline.toString();
                    dialogStates.put(userId, new DialogState(DialogType.ADD_TASK, 2, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 3/4: Выберите приоритет:\n1 — HIGH (высокий)\n2 — MEDIUM (средний)\n3 — LOW (низкий)"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат даты. Пример: 2026-06-01T23:59\nПовторите:"));
                }
            }
            case 2 -> {
                TaskPriority priority = parsePriority(text.trim());
                if (priority == null) yield Mono.just(BotMessageResponse.error("Введите 1 (HIGH), 2 (MEDIUM) или 3 (LOW):"));
                data[2] = priority.name();
                dialogStates.put(userId, new DialogState(DialogType.ADD_TASK, 3, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 4/4: Выберите тип задачи:\n1 — HOMEWORK (домашнее задание)\n2 — LAB (лабораторная)\n3 — COURSEWORK (курсовая)\n4 — OTHER (другое)"));
            }
            case 3 -> {
                TaskType type = parseType(text.trim());
                if (type == null) yield Mono.just(BotMessageResponse.error("Введите 1 (HOMEWORK), 2 (LAB), 3 (COURSEWORK) или 4 (OTHER):"));
                dialogStates.remove(userId);
                yield taskModule.createTask(userId, data[0], Instant.parse(data[1]),
                        TaskPriority.valueOf(data[2]), type)
                        .map(task -> BotMessageResponse.ok("✅ Задача добавлена: " + task.title() +
                                "\n⏰ Дедлайн: " + formatDateTime(task.deadline(), ZoneId.of("Europe/Moscow"))))
                        .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
            }
            default -> {
                dialogStates.remove(userId);
                yield Mono.just(BotMessageResponse.error("Ошибка диалога."));
            }
        };
    }
    private Mono<BotMessageResponse> startEditTaskDialog(String userId) {
        return taskModule.allOpenTasks(userId).collectList().map(tasks -> {
            if (tasks.isEmpty()) return BotMessageResponse.ok("У вас нет открытых задач для редактирования.");
            String[] ids = tasks.stream().map(TaskItem::id).toArray(String[]::new);
            dialogStates.put(userId, new DialogState(DialogType.EDIT_TASK_SELECT, 0, ids));
            return BotMessageResponse.ok("✏️ Выберите задачу (введите номер):\n\n" + formatTasksNumbered(tasks));
        });
    }

    private Mono<BotMessageResponse> handleEditTaskSelect(String userId, String text, DialogState state) {
        try {
            int num = Integer.parseInt(text.trim()) - 1;
            if (num < 0 || num >= state.data().length)
                return Mono.just(BotMessageResponse.error("Неверный номер. Введите от 1 до " + state.data().length));
            String taskId = state.data()[num];
            dialogStates.put(userId, new DialogState(DialogType.EDIT_TASK_FIELD, 0, new String[]{taskId, "", "", "", ""}));
            return Mono.just(BotMessageResponse.ok("Шаг 1/4: Введите новое название задачи:"));
        } catch (NumberFormatException e) {
            return Mono.just(BotMessageResponse.error("Введите номер задачи:"));
        }
    }

    private Mono<BotMessageResponse> handleEditTaskField(String userId, String text, DialogState state) {
        String[] data = state.data();
        int step = state.step();
        data[step + 1] = text.trim();

        return switch (step) {
            case 0 -> {
                dialogStates.put(userId, new DialogState(DialogType.EDIT_TASK_FIELD, 1, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 2/4: Введите новый дедлайн в вашем часовом поясе:\nПример: 2026-06-01T23:59"));
            }
            case 1 -> {
                try {
                    ZoneId userZone = ZoneId.of("Europe/Moscow");
                    Instant deadline = parseUserDateTime(text.trim(), userZone);
                    if (deadline.isBefore(Instant.now())) {
                        yield Mono.just(BotMessageResponse.error("❌ Дедлайн не может быть в прошлом. Введите заново:"));
                    }
                    data[2] = deadline.toString();
                    dialogStates.put(userId, new DialogState(DialogType.EDIT_TASK_FIELD, 2, data));
                    yield Mono.just(BotMessageResponse.ok("Шаг 3/4: Приоритет:\n1 — HIGH\n2 — MEDIUM\n3 — LOW"));
                } catch (Exception e) {
                    yield Mono.just(BotMessageResponse.error("Неверный формат. Пример: 2026-06-01T23:59\nПовторите:"));
                }
            }
            case 2 -> {
                TaskPriority priority = parsePriority(text.trim());
                if (priority == null) yield Mono.just(BotMessageResponse.error("Введите 1, 2 или 3:"));
                data[3] = priority.name();
                dialogStates.put(userId, new DialogState(DialogType.EDIT_TASK_FIELD, 3, data));
                yield Mono.just(BotMessageResponse.ok("Шаг 4/4: Тип:\n1 — HOMEWORK\n2 — LAB\n3 — COURSEWORK\n4 — OTHER"));
            }
            case 3 -> {
                TaskType type = parseType(text.trim());
                if (type == null) yield Mono.just(BotMessageResponse.error("Введите 1, 2, 3 или 4:"));
                dialogStates.remove(userId);
                yield taskModule.updateTask(userId, data[0], data[1], Instant.parse(data[2]),
                        TaskPriority.valueOf(data[3]), type)
                        .map(task -> BotMessageResponse.ok("✅ Задача обновлена: " + task.title()))
                        .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
            }
            default -> {
                dialogStates.remove(userId);
                yield Mono.just(BotMessageResponse.error("Ошибка диалога."));
            }
        };
    }
    private Mono<BotMessageResponse> startDoneTaskDialog(String userId) {
        return taskModule.allOpenTasks(userId).collectList().map(tasks -> {
            if (tasks.isEmpty()) return BotMessageResponse.ok("У вас нет открытых задач.");
            String[] ids = tasks.stream().map(TaskItem::id).toArray(String[]::new);
            dialogStates.put(userId, new DialogState(DialogType.DONE_TASK, 0, ids));
            return BotMessageResponse.ok("✅ Выберите задачу для отметки выполненной (введите номер):\n\n" + formatTasksNumbered(tasks));
        });
    }

    private Mono<BotMessageResponse> handleDoneTaskSelect(String userId, String text, DialogState state) {
        try {
            int num = Integer.parseInt(text.trim()) - 1;
            if (num < 0 || num >= state.data().length)
                return Mono.just(BotMessageResponse.error("Неверный номер. Введите от 1 до " + state.data().length));
            String taskId = state.data()[num];
            dialogStates.remove(userId);
            return taskModule.markDone(userId, taskId)
                    .map(task -> BotMessageResponse.ok("✅ Задача выполнена: " + task.title()))
                    .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
        } catch (NumberFormatException e) {
            return Mono.just(BotMessageResponse.error("Введите номер задачи:"));
        }
    }
    private Mono<BotMessageResponse> startDeleteTaskDialog(String userId) {
        return taskModule.allOpenTasks(userId).collectList().map(tasks -> {
            if (tasks.isEmpty()) return BotMessageResponse.ok("У вас нет задач для удаления.");
            String[] ids = tasks.stream().map(TaskItem::id).toArray(String[]::new);
            dialogStates.put(userId, new DialogState(DialogType.DELETE_TASK, 0, ids));
            return BotMessageResponse.ok("🗑 Выберите задачу для удаления (введите номер):\n\n" + formatTasksNumbered(tasks));
        });
    }

    private Mono<BotMessageResponse> handleDeleteTaskSelect(String userId, String text, DialogState state) {
        try {
            int num = Integer.parseInt(text.trim()) - 1;
            if (num < 0 || num >= state.data().length)
                return Mono.just(BotMessageResponse.error("Неверный номер. Введите от 1 до " + state.data().length));
            String taskId = state.data()[num];
            dialogStates.remove(userId);
            return taskModule.deleteTask(userId, taskId)
                    .thenReturn(BotMessageResponse.ok("✅ Задача удалена."))
                    .onErrorResume(ex -> Mono.just(BotMessageResponse.error("Ошибка: " + ex.getMessage())));
        } catch (NumberFormatException e) {
            return Mono.just(BotMessageResponse.error("Введите номер задачи:"));
        }
    }
    private Mono<BotMessageResponse> handleTaskPriority(String userId, String args) {
        TaskPriority priority = parsePriorityFromString(args.trim());
        if (priority == null)
            return Mono.just(BotMessageResponse.error("Укажите приоритет: /task_priority high | medium | low"));
        return taskModule.tasksByPriority(userId, priority).collectList()
                .map(tasks -> formatTasks("Задачи [" + priority.name() + "]", tasks));
    }

    private Mono<BotMessageResponse> handleTaskType(String userId, String args) {
        TaskType type = parseTypeFromString(args.trim());
        if (type == null)
            return Mono.just(BotMessageResponse.error("Укажите тип: /task_type homework | lab | coursework | other"));
        return taskModule.tasksByType(userId, type).collectList()
                .map(tasks -> formatTasks("Задачи [" + type.name() + "]", tasks));
    }
    private Mono<BotMessageResponse> setTimezone(String userId, String args) {
        String tz = args.trim();
        if (tz.isEmpty()) {
            return Mono.just(BotMessageResponse.ok("""
⚙️ Укажите часовой пояс. Примеры:
/set_timezone Europe/Moscow (UTC+3, Москва)
/set_timezone Europe/Kaliningrad (UTC+2)
/set_timezone Asia/Yekaterinburg (UTC+5)
/set_timezone Asia/Novosibirsk (UTC+7)
/set_timezone Asia/Vladivostok (UTC+10)
/set_timezone UTC"""));
        }
        return userModule.setTimezone(userId, tz)
                .map(p -> BotMessageResponse.ok("✅ Часовой пояс установлен: " + p.timezone() +
                        "\nТеперь все время отображается в вашем часовом поясе."))
                .onErrorResume(ex -> Mono.just(BotMessageResponse.error(
                        "❌ " + ex.getMessage() + "\nПример: /set_timezone Europe/Moscow")));
    }

    private Mono<BotMessageResponse> myTimezone(String userId) {
        return userModule.createIfAbsent(userId)
                .map(p -> BotMessageResponse.ok("🕐 Ваш часовой пояс: " + p.timezone() +
                        "\nИзменить: /set_timezone Europe/Moscow"));
    }
    private Mono<BotMessageResponse> askLlm(String args) {
        if (args.trim().isEmpty()) return Mono.just(BotMessageResponse.error("Введите вопрос: /ask <вопрос>"));
        return llmAdvisorModule.askFreeForm(args.trim()).map(BotMessageResponse::ok);
    }

    private Mono<BotMessageResponse> advisePriority(String userId) {
        return userModule.createIfAbsent(userId).flatMap(profile -> {
            ZoneId zone = ZoneId.of(profile.timezone());
            return Mono.zip(taskModule.tasksForWeek(userId, zone).collectList(),
                    scheduleModule.eventsForWeek(userId, zone).collectList());
        }).flatMap(t -> llmAdvisorModule.advisePriority(t.getT1(), t.getT2())).map(BotMessageResponse::ok);
    }

    private Mono<BotMessageResponse> studyPlan(String args) {
        if (args.trim().isEmpty())
            return Mono.just(BotMessageResponse.error("Формат: /study_plan Математика | 2026-06-10T09:00"));
        String[] parts = args.split("\\s*\\|\\s*", 3);
        String subject = parts[0].trim();
        String date = parts.length > 1 ? parts[1].trim() : "не указана";
        String extra = parts.length > 2 ? parts[2].trim() : "";
        return llmAdvisorModule.generateStudyPlan(subject, date, extra).map(BotMessageResponse::ok);
    }
    private String formatDateTime(Instant instant, ZoneId zone) {
        ZonedDateTime zdt = instant.atZone(zone);
        return zdt.format(DATE_FMT) + " " + zdt.format(TIME_FMT);
    }

        private String formatEvents(String header, List<ScheduleEvent> events) {
        return formatEvents(header, events, ZoneId.of("Europe/Moscow"));
    }

    private String formatEvents(String header, List<ScheduleEvent> events, ZoneId zone) {
        if (events.isEmpty()) return header + ": событий не найдено.";
        StringBuilder b = new StringBuilder(header + ":\n");
        for (ScheduleEvent e : events) {
            b.append("• [").append(e.type()).append("] ").append(e.title())
                    .append("\n  📅 ").append(formatDateTime(e.startsAt(), zone))
                    .append(" — ").append(e.endsAt().atZone(zone).format(TIME_FMT))
                    .append(" @ ").append(e.place()).append("\n");
        }
        return b.toString().trim();
    }

    private String formatEventsNumbered(List<ScheduleEvent> events) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            ScheduleEvent e = events.get(i);
                        b.append(i + 1).append(". [").append(e.type()).append("] ").append(e.title())
                    .append("\n   📅 ").append(formatDateTime(e.startsAt(), zone))
                    .append(" — ").append(e.endsAt().atZone(zone).format(TIME_FMT))
                    .append(" @ ").append(e.place()).append("\n");
        }
        return b.toString().trim();
    }

        private BotMessageResponse formatAllTasks(List<TaskItem> tasks) {
        return formatAllTasks(tasks, ZoneId.of("Europe/Moscow"));
    }

    private BotMessageResponse formatAllTasks(List<TaskItem> tasks, ZoneId zone) {
        if (tasks.isEmpty()) return BotMessageResponse.ok("Все открытые задачи: задач не найдено.");
        Instant now = Instant.now();
        StringBuilder open = new StringBuilder();
        StringBuilder overdue = new StringBuilder();

        for (TaskItem t : tasks) {
            String line = "⏳ " + t.title() +
                    "\n  " + t.type() + " | " + t.priority() +
                    " | Дедлайн: " + formatDateTime(t.deadline(), zone) + "\n";
            if (t.deadline().isBefore(now)) {
                overdue.append("⚠️ ").append(line.substring(2)); // меняем иконку
            } else {
                open.append(line);
            }
        }

        StringBuilder result = new StringBuilder("📝 Все открытые задачи:\n\n");
        if (!open.isEmpty()) {
            result.append("Активные:\n").append(open);
        }
        if (!overdue.isEmpty()) {
            if (!open.isEmpty()) result.append("\n");
            result.append("⚠️ Просроченные:\n").append(overdue);
        }
        return BotMessageResponse.ok(result.toString().trim());
    }

    private BotMessageResponse formatTasks(String header, List<TaskItem> tasks) {
        return formatTasks(header, tasks, ZoneId.of("Europe/Moscow"));
    }

    private BotMessageResponse formatTasks(String header, List<TaskItem> tasks, ZoneId zone) {
        if (tasks.isEmpty()) return BotMessageResponse.ok(header + ": задач не найдено.");
        Instant now = Instant.now();
        StringBuilder b = new StringBuilder(header + ":\n");
        for (TaskItem t : tasks) {
                        String icon = t.deadline().isBefore(now) ? "⚠️" : "⏳";
            b.append(icon).append(" ").append(t.title())
                    .append("\n  ").append(t.type()).append(" | ").append(t.priority())
                    .append(" | Дедлайн: ").append(formatDateTime(t.deadline(), zone)).append("\n");
        }
        return BotMessageResponse.ok(b.toString().trim());
    }

    private String formatTasksNumbered(List<TaskItem> tasks) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            TaskItem t = tasks.get(i);
            b.append(i + 1).append(". ").append(t.title())
                    .append(" [").append(t.priority()).append("]")
                    .append(" — ").append(formatDateTime(t.deadline(), zone)).append("\n");
        }
        return b.toString().trim();
    }

    private BotMessageResponse formatImportHistory(List<ImportHistoryItem> imports) {
        if (imports.isEmpty()) return BotMessageResponse.ok("Импорты: история пуста.");
        ZoneId zone = ZoneId.of("Europe/Moscow");
        StringBuilder b = new StringBuilder("Последние импорты:\n");
        for (ImportHistoryItem item : imports) {
            String time = item.createdAt() != null ? formatDateTime(item.createdAt(), zone) : "?";
            b.append("• ").append(time).append(" | ").append(item.source())
                    .append(" | ").append(localizeStatus(item.status()))
                    .append(" | загружено: ").append(item.importedCount());
            if (item.errorMessage() != null && !item.errorMessage().isBlank())
                b.append(" | ошибка: ").append(truncate(item.errorMessage(), 100));
            b.append("\n");
        }
        return BotMessageResponse.ok(b.toString().trim());
    }

    private String localizeStatus(com.timetablebot.domain.schedule.ImportStatus status) {
        return switch (status) {
            case SUCCESS -> "УСПЕХ";
            case PARTIAL -> "ЧАСТИЧНО";
            case ERROR -> "ОШИБКА";
        };
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
    private Instant parseUserDateTime(String text, ZoneId userZone) {
        String t = text.trim();
        if (t.endsWith("Z") || t.matches(".*[+-]\\d{2}:\\d{2}$")) {
            return java.time.Instant.parse(t.endsWith("Z") ? t :
                    java.time.OffsetDateTime.parse(t).toInstant().toString());
        }
        if (t.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
            t = t + ":00";
        }
        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(t);
        return ldt.atZone(userZone).toInstant();
    }

    private TaskPriority parsePriority(String text) {
        return switch (text.toLowerCase()) {
            case "1", "high" -> TaskPriority.HIGH;
            case "2", "medium" -> TaskPriority.MEDIUM;
            case "3", "low" -> TaskPriority.LOW;
            default -> null;
        };
    }

    private TaskPriority parsePriorityFromString(String text) {
        try { return TaskPriority.valueOf(text.toUpperCase()); } catch (Exception e) { return null; }
    }

    private TaskType parseType(String text) {
        return switch (text.toLowerCase()) {
            case "1", "homework" -> TaskType.HOMEWORK;
            case "2", "lab" -> TaskType.LAB;
            case "3", "coursework" -> TaskType.COURSEWORK;
            case "4", "other" -> TaskType.OTHER;
            default -> null;
        };
    }

    private TaskType parseTypeFromString(String text) {
        try { return TaskType.valueOf(text.toUpperCase()); } catch (Exception e) { return null; }
    }

    private BotMessageResponse startResponse(UserProfile profile) {
        String text = profile.isNewUser()
                ? "Добро пожаловать! Ваш профиль создан.\nИспользуйте /menu для списка команд или /help для примеров."
                : "С возвращением!\nИспользуйте /menu для списка команд.";
        return BotMessageResponse.ok(text);
    }
    private enum DialogType {
        ADD_EVENT, EDIT_EVENT_SELECT, EDIT_EVENT_FIELD, DELETE_EVENT,
        IMPORT_TIMETABLE_CHOOSE, IMPORT_TIMETABLE_TEXT, IMPORT_TIMETABLE_FILE,
        ADD_TASK, EDIT_TASK_SELECT, EDIT_TASK_FIELD, DONE_TASK, DELETE_TASK
    }

    private record DialogState(DialogType type, int step, String[] data) {}
}
