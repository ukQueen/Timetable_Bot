# Timetable_Bot
Lessons and Exams Timetable Bot


## Telegram настройки
- `TELEGRAM_BOT_ENABLED` — включить отправку сообщений в Telegram API.
- `TELEGRAM_BOT_TOKEN` — токен бота.
- `TELEGRAM_WEBHOOK_SECRET` — секрет для заголовка `X-Telegram-Bot-Api-Secret-Token`.
- `TELEGRAM_WEBHOOK_URL` — публичный URL webhook (например `https://example.com/telegram/webhook`).
- `TELEGRAM_REGISTER_WEBHOOK_ON_STARTUP` — при `true` приложение вызывает `setWebhook` на старте.