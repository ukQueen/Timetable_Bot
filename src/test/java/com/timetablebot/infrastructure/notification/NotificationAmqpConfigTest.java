package com.timetablebot.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class NotificationAmqpConfigTest {

    private final NotificationAmqpConfig config = new NotificationAmqpConfig();

    @Test
    void shouldCreateJacksonMessageConverter() {
        MessageConverter converter = config.notificationMessageConverter(new ObjectMapper());
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }

    @Test
    void shouldWireRabbitTemplateWithConverter() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        MessageConverter converter = config.notificationMessageConverter(new ObjectMapper());

        RabbitTemplate rabbitTemplate = config.rabbitTemplate(connectionFactory, converter);

        assertNotNull(rabbitTemplate);
        assertInstanceOf(Jackson2JsonMessageConverter.class, rabbitTemplate.getMessageConverter());
    }

    @Test
    void shouldCreateListenerFactoryWithConverter() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        MessageConverter converter = config.notificationMessageConverter(new ObjectMapper());

        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(connectionFactory, converter);

        assertNotNull(factory);
    }
}