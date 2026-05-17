# Timetable_Bot
Lessons and Exams Timetable Bot

## Локальный запуск
### Требования
- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### Поднять зависимости
```bash
docker compose up -d
```

### Запустить приложение
```bash
mvn spring-boot:run
```

### Проверка healthcheck
```bash
curl -H "X-Request-Id: local-check-1" http://localhost:8080/healthcheck
```

## Команды бота
- `/start` — создать профиль или получить приветствие для существующего пользователя.
- `/menu` — список доступных команд.
- `/today`, `/tomorrow`, `/week` — просмотр расписания.
- `/add_event`, `/edit_event`, `/delete_event` — ручное управление событиями.
- `/import_timetable <CSV или iCal>` — импорт расписания из текста CSV/iCal.
- `/import_external <url_csv>` — импорт CSV по внешнему URL.
- `/imports` — показать последние попытки импорта (статус, источник, время, ошибки).
- `/add_task`, `/edit_task`, `/done_task`, `/delete_task` — управление задачами.
- `/tasks_today`, `/tasks_week`, `/tasks_overdue` — выборки задач.

## Примеры импорта
### CSV
Формат строки: `TYPE,TITLE,PLACE,START_ISO,END_ISO`
```text
LESSON,Math,A-101,2026-05-13T10:00:00Z,2026-05-13T11:00:00Z
EXAM,Physics,B-210,2026-06-01T08:00:00Z,2026-06-01T10:00:00Z
```

### iCal
```text
BEGIN:VCALENDAR
BEGIN:VEVENT
SUMMARY:Physics
LOCATION:B-210
DTSTART:20260601T080000Z
DTEND:20260601T100000Z
CATEGORIES:EXAM
END:VEVENT
END:VCALENDAR
```

## Telegram настройки
- `TELEGRAM_BOT_ENABLED` — включить отправку сообщений в Telegram API.
- `TELEGRAM_BOT_TOKEN` — токен бота.
- `TELEGRAM_WEBHOOK_SECRET` — секрет для заголовка `X-Telegram-Bot-Api-Secret-Token`.
- `TELEGRAM_WEBHOOK_URL` — публичный URL webhook (например `https://example.com/telegram/webhook`).
- `TELEGRAM_REGISTER_WEBHOOK_ON_STARTUP` — при `true` приложение вызывает `setWebhook` на старте.

## Notifications / RabbitMQ настройки
- `NOTIFICATIONS_QUEUE` — основная очередь уведомлений.
- `TASK_REMINDER_LEAD_MINUTES` — за сколько минут до дедлайна создавать напоминание в scheduler.
- `NOTIFICATIONS_SCHEDULER_FIXED_DELAY_MS` — период запуска scheduler в миллисекундах.
- `NOTIFICATIONS_DLQ_EXCHANGE` — dead-letter exchange для неуспешных уведомлений.
- `NOTIFICATIONS_DLQ_QUEUE` — dead-letter queue для сообщений, которые не удалось обработать.
- `NOTIFICATIONS_DLQ_ROUTING_KEY` — routing key для маршрутизации в DLQ.

## Текущее поведение уведомлений
- Scheduler (`TaskReminderScheduler`) выбирает ближайшие `OPEN` задачи и публикует напоминания в `NOTIFICATIONS_QUEUE`.
- Consumer (`NotificationModule`) читает очередь и отправляет сообщение в Telegram.
- При неуспешной доставке или невалидном payload сообщение не реqueue-ится бесконечно и уходит в DLQ по настроенной политике.

## Healthcheck
Endpoint: `GET /healthcheck`

Возвращает:
- `status`: `UP | DEGRADED | DOWN`
- `request_id`: корреляционный идентификатор запроса (`X-Request-Id`)
- `dependencies`: статусы `mongodb`, `rabbitmq`, `telegram`
