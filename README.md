# Timetable_Bot
Lessons and Exams Timetable Bot
### DockerHub Link
- Docker Hub Link: https://hub.docker.com/r/infernotawer/timetable-bot
## Запуск с докерхаба
- Создать директорию у себя на компьютере
- Добавить в нее файл .env по примеру .env.example со всеми токенами и своей ссылкой, полученной, например, через ngrok.
```env
# Telegram bot
TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_TOKEN=your-token
TELEGRAM_BOT_WEBHOOK_SECRET=random-secret
TELEGRAM_BOT_WEBHOOK_URL=your-link/telegram/webhook
TELEGRAM_BOT_REGISTER_WEBHOOK_ON_STARTUP=true

# LLM API
LLM_API_ENABLED=true
LLM_API_KEY=your-token
```
- Добавить в нее файл docker-compose.yml
- Перейти в созданную директорию и запустить бота командой
```bash
docker compose up -d
```
- Проверить запуск с помощью healthcheck
```bash
curl http://localhost:8080/healthcheck
```

## HTTP API (admin)
1. Получить токен администратора:
```bash
curl -X POST http://localhost:8080/auth   -H "Content-Type: application/json"   -d '{"username":"admin","password":"admin123"}'
```
2. Проверить доступ администратора:
```bash
curl http://localhost:8080/admin/whoami -H "Authorization: Bearer <ADMIN_TOKEN>"
```
3. Получить список пользователей:
```bash
curl http://localhost:8080/admin/users -H "Authorization: Bearer <ADMIN_TOKEN>"
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
