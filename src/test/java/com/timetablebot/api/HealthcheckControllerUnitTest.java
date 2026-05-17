package com.timetablebot.api;

import com.rabbitmq.client.Channel;
import com.timetablebot.infrastructure.observability.RequestIdWebFilter;
import com.timetablebot.infrastructure.telegram.TelegramBotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class HealthcheckControllerUnitTest {

    @Test
    void shouldReturnUpWhenDependenciesAreAvailable() {
        ReactiveMongoTemplate mongoTemplate = mock(ReactiveMongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(reactor.core.publisher.Mono.just(new org.bson.Document("ok", 1)));

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);

        HealthcheckController controller = new HealthcheckController(provider(mongoTemplate), provider(connectionFactory), provider(new TelegramBotProperties("token", true, "", "", false)));
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, "/healthcheck")
                .header(RequestIdWebFilter.REQUEST_ID_HEADER, "req-1")
                .build();

        StepVerifier.create(controller.healthcheck(request))
                .assertNext(payload -> {
                    org.junit.jupiter.api.Assertions.assertEquals("UP", payload.get("status"));
                    org.junit.jupiter.api.Assertions.assertEquals("req-1", payload.get("request_id"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnDownWhenProvidersUnavailable() {
        HealthcheckController controller = new HealthcheckController(provider(null), provider(null), provider(null));

        StepVerifier.create(controller.healthcheck(MockServerHttpRequest.get("/healthcheck").build()))
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("DOWN", payload.get("status")))
                .verifyComplete();
    }

    @Test
    void shouldReturnDegradedWhenMongoFails() {
        ReactiveMongoTemplate mongoTemplate = mock(ReactiveMongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(reactor.core.publisher.Mono.error(new RuntimeException("mongo down")));

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);

        HealthcheckController controller = new HealthcheckController(provider(mongoTemplate), provider(connectionFactory), provider(new TelegramBotProperties("token", true, "", "", false)));

        StepVerifier.create(controller.healthcheck(MockServerHttpRequest.get("/healthcheck").build()))
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("DEGRADED", payload.get("status")))
                .verifyComplete();
    }

    @Test
    void shouldReturnDegradedWhenRabbitFails() {
        ReactiveMongoTemplate mongoTemplate = mock(ReactiveMongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(reactor.core.publisher.Mono.just(new org.bson.Document("ok", 1)));

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.createConnection()).thenThrow(new RuntimeException("rabbit down"));

        HealthcheckController controller = new HealthcheckController(provider(mongoTemplate), provider(connectionFactory), provider(new TelegramBotProperties("token", true, "", "", false)));

        StepVerifier.create(controller.healthcheck(MockServerHttpRequest.get("/healthcheck").build()))
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("DEGRADED", payload.get("status")))
                .verifyComplete();
    }

    @Test
    void shouldReturnDegradedWhenTelegramEnabledWithoutToken() {
        ReactiveMongoTemplate mongoTemplate = mock(ReactiveMongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(reactor.core.publisher.Mono.just(new org.bson.Document("ok", 1)));

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);

        TelegramBotProperties properties = new TelegramBotProperties("", true, "", "", false);
        HealthcheckController controller = new HealthcheckController(provider(mongoTemplate), provider(connectionFactory), provider(properties));

        StepVerifier.create(controller.healthcheck(MockServerHttpRequest.get("/healthcheck").build()))
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("DEGRADED", payload.get("status")))
                .verifyComplete();
    }

    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
