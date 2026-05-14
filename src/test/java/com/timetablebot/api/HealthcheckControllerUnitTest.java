package com.timetablebot.api;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
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

        HealthcheckController controller = new HealthcheckController(provider(mongoTemplate), provider(connectionFactory));

        StepVerifier.create(controller.healthcheck())
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("UP", payload.get("status")))
                .verifyComplete();
    }

    @Test
    void shouldReturnDegradedWhenProvidersUnavailable() {
        HealthcheckController controller = new HealthcheckController(provider(null), provider(null));

        StepVerifier.create(controller.healthcheck())
                .assertNext(payload -> org.junit.jupiter.api.Assertions.assertEquals("DEGRADED", payload.get("status")))
                .verifyComplete();
    }

    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}