# Timetable_Bot
Lessons and Exams Timetable Bot

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