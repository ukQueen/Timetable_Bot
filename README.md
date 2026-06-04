# Timetable_Bot
Lessons and Exams Timetable Bot
### DockerHub Link
- Docker Hub Link: https://hub.docker.com/r/infernotawer/timetable-bot
## Запуск с докерхаба
- Создать директорию у себя на компьютере
- Добавить в нее файл .env со всеми токенами и ссылкой на, например, ngrok.
- Добавить в нее файл docker-compose.yml
- Перейти в созданную директорию и запустить бота командой
```bash
docker compose up -d
```
- Проверить запуск с помощью healthcheck
```bash
curl http://localhost:8080/healthcheck
```
## Локальный запуск
### Требования
- Java 25+
- Maven 3.9+
- Docker + Docker Compose

### Запуск всего приложения в Docker (рекомендуется)
```bash
docker compose up -d --build
```

Это поднимет `mongodb`, `rabbitmq` и `app` как отдельные контейнеры.

Логи приложения:
```bash
docker compose logs -f app
```

Остановка:
```bash
docker compose down
```

### Альтернатива: локальный запуск приложения без контейнера
```bash
docker compose up -d mongodb rabbitmq
mvn package
java -jar target/timetable-bot-0.1.0-SNAPSHOT.jar
```

По умолчанию сервер стартует на `8080`. Для изменения порта задай `SERVER_PORT`, например:
```bash
SERVER_PORT=8090 java -jar target/timetable-bot-0.1.0-SNAPSHOT.jar
```

### Troubleshooting
- Если `docker compose` показывает предупреждение про `version is obsolete`, убедись что в `docker-compose.yml` нет поля `version` (в этом проекте уже удалено).
- Если `mvn package` падает на `testCompile`, обнови ветку (`git pull`) и временно запусти: `mvn -DskipTests package`, затем `java -jar target/timetable-bot-0.1.0-SNAPSHOT.jar`.

### Проверка healthcheck
```bash
curl -H "X-Request-Id: local-check-1" http://localhost:8080/healthcheck
```

## Submission checklist
- GitHub Repository Link: `<ADD_GITHUB_REPO_LINK>`
- Docker Hub Link: `<ADD_DOCKER_HUB_LINK>`
- Telegram Bot Link or Username: `<ADD_TELEGRAM_BOT_LINK_OR_USERNAME>`

## Docker deployment (submission-ready)
1. Build image locally:
```bash
docker build -t <dockerhub_user>/timetable-bot:latest .
```
2. Push to Docker Hub:
```bash
docker push <dockerhub_user>/timetable-bot:latest
```
3. Run published image:
```bash
docker run --rm -p 8080:8080 --env-file .env <dockerhub_user>/timetable-bot:latest
```

## Telegram bot interaction
1. Configure `.env` (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_URL`, `TELEGRAM_WEBHOOK_SECRET`).
2. Start stack (`docker compose up -d --build`).
3. Open your bot in Telegram and use `/start`, then `/menu`.


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

## Секреты в .env
1. Создай файл `.env` в корне проекта (рядом с `pom.xml`).
2. Добавь туда секреты и переменные окружения. Пример:

```env
TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_TOKEN=123456:ABCDEF
TELEGRAM_WEBHOOK_SECRET=your-webhook-secret
TELEGRAM_WEBHOOK_URL=https://example.com/telegram/webhook
TELEGRAM_REGISTER_WEBHOOK_ON_STARTUP=true

ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me
ADMIN_TOKEN=change-me
```

Приложение загружает `.env` через `DotenvPropertyLoader` при старте (до инициализации Spring-контекста).

## Webhook: локально через ngrok и прод через домен сервера
Подход один и тот же: меняется только значение `TELEGRAM_WEBHOOK_URL`.

### Локально (ngrok)
1. Запусти приложение: `mvn -DskipTests package` и `java -jar target/timetable-bot-0.1.0-SNAPSHOT.jar`
2. Подними туннель: `ngrok http 8080`
3. Возьми HTTPS URL из ngrok и укажи в `.env`:

```env
TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_TOKEN=<BOTFATHER_TOKEN>
TELEGRAM_WEBHOOK_SECRET=<RANDOM_SECRET>
TELEGRAM_WEBHOOK_URL=https://<your-ngrok-domain>/telegram/webhook
TELEGRAM_REGISTER_WEBHOOK_ON_STARTUP=true
```

### Прод/сервер
На сервере используй тот же конфиг, но с доменом сервера:

```env
TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_TOKEN=<BOTFATHER_TOKEN>
TELEGRAM_WEBHOOK_SECRET=<RANDOM_SECRET>
TELEGRAM_WEBHOOK_URL=https://<your-server-domain>/telegram/webhook
TELEGRAM_REGISTER_WEBHOOK_ON_STARTUP=true
```

Важно:
- `TELEGRAM_WEBHOOK_URL` должен быть публичным HTTPS URL.
- Endpoint webhook в приложении: `/telegram/webhook`.
- При смене URL (ngrok -> сервер) просто меняешь `TELEGRAM_WEBHOOK_URL` и перезапускаешь приложение.

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
