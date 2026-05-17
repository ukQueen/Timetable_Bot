package com.timetablebot.infrastructure.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookRegistrarTest {

    @Mock
    private TelegramBotClient telegramBotClient;

    @Test
    void shouldRegisterWebhookWhenEnabledAndConfigured() {
        TelegramBotProperties props = new TelegramBotProperties("token", true, "secret", "https://example.com/telegram/webhook", true);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(telegramBotClient, props);

        when(telegramBotClient.registerWebhook(anyString(), anyString())).thenReturn(Mono.empty());

        registrar.run(new DefaultApplicationArguments(new String[]{}));

        verify(telegramBotClient).registerWebhook("https://example.com/telegram/webhook", "secret");
    }

    @Test
    void shouldSkipWhenWebhookUrlEmpty() {
        TelegramBotProperties props = new TelegramBotProperties("token", true, "secret", "", true);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(telegramBotClient, props);

        registrar.run(new DefaultApplicationArguments(new String[]{}));

        verify(telegramBotClient, never()).registerWebhook(anyString(), anyString());
    }

    @Test
    void shouldSkipWhenBotDisabled() {
        TelegramBotProperties props = new TelegramBotProperties("token", false, "secret", "https://example.com/telegram/webhook", true);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(telegramBotClient, props);

        registrar.run(new DefaultApplicationArguments(new String[]{}));

        verify(telegramBotClient, never()).registerWebhook(anyString(), anyString());
    }
}