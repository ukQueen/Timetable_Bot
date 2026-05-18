package com.timetablebot.api;

import com.rabbitmq.client.Channel;
import com.timetablebot.infrastructure.telegram.TelegramBotClient;
import com.timetablebot.infrastructure.telegram.TelegramBotProperties;
import com.timetablebot.infrastructure.observability.RequestIdWebFilter;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthcheckController {

    private static final Duration DEPENDENCY_TIMEOUT = Duration.ofMillis(500);

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;
    private final ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider;
    private final ObjectProvider<TelegramBotProperties> telegramBotPropertiesProvider;
    private final ObjectProvider<TelegramBotClient> telegramBotClientProvider;

    public HealthcheckController(ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider,
                                 ObjectProvider<ConnectionFactory> rabbitConnectionFactoryProvider,
                                 ObjectProvider<TelegramBotProperties> telegramBotPropertiesProvider,
                                 ObjectProvider<TelegramBotClient> telegramBotClientProvider) {
        this.mongoTemplateProvider = mongoTemplateProvider;
        this.rabbitConnectionFactoryProvider = rabbitConnectionFactoryProvider;
        this.telegramBotPropertiesProvider = telegramBotPropertiesProvider;
        this.telegramBotClientProvider = telegramBotClientProvider;
    }


    @GetMapping(value = "/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> healthcheck(ServerHttpRequest request) {
        return Mono.zip(mongoHealth(), rabbitHealth(), telegramHealth())
            .map(tuple -> {
                String mongoStatus = tuple.getT1();
                String rabbitStatus = tuple.getT2();
                String telegramStatus = tuple.getT3();
                String status = overallStatus(mongoStatus, rabbitStatus, telegramStatus);

                    Map<String, String> dependencies = new LinkedHashMap<>();
                    dependencies.put("mongodb", mongoStatus);
                    dependencies.put("rabbitmq", rabbitStatus);
                    dependencies.put("telegram", telegramStatus);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", status);
                    response.put("service", "timetable-bot");
                    response.put("timestamp", Instant.now().toString());
                    response.put("request_id", currentRequestId(request));
                    response.put("dependencies", dependencies);
                    return response;
                });
    }

    private String currentRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(RequestIdWebFilter.REQUEST_ID_HEADER);
        return requestId == null ? "unknown" : requestId;
    }

    private String overallStatus(String mongoStatus, String rabbitStatus, String telegramStatus) {
        if (isDown(mongoStatus) || isDown(rabbitStatus)) {
            return "DOWN";
        }
        if (isDegraded(mongoStatus) && isDegraded(rabbitStatus)) {
            return "DOWN";
        }
        if (isDegraded(mongoStatus) || isDegraded(rabbitStatus) || isDegraded(telegramStatus)) {
            return "DEGRADED";
        }
        return "UP";
    }

    private Mono<String> mongoHealth() {
        ReactiveMongoTemplate mongoTemplate = mongoTemplateProvider.getIfAvailable();
        if (mongoTemplate == null) {
            return Mono.just("UNKNOWN");
        }

        return mongoTemplate.executeCommand("{ ping: 1 }")
                .timeout(DEPENDENCY_TIMEOUT)
                .map(result -> "UP")
                .onErrorReturn("DEGRADED");
    }

    private Mono<String> rabbitHealth() {
        ConnectionFactory connectionFactory = rabbitConnectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return Mono.just("UNKNOWN");
        }

        return Mono.fromCallable(() -> {
                    try (Connection connection = connectionFactory.createConnection();
                         Channel channel = connection.createChannel(false)) {
                        return (connection.isOpen() && channel.isOpen()) ? "UP" : "DEGRADED";
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(DEPENDENCY_TIMEOUT)
                .onErrorReturn("DEGRADED");
    }

    private Mono<String> telegramHealth() {
        TelegramBotProperties properties = telegramBotPropertiesProvider.getIfAvailable();
        if (properties == null || !properties.enabled()) {
            return Mono.just("UNKNOWN");
        }

        TelegramBotClient telegramBotClient = telegramBotClientProvider.getIfAvailable();
        if (telegramBotClient == null) {
            return Mono.just("DEGRADED");
        }

        return telegramBotClient.healthProbe()
                .timeout(DEPENDENCY_TIMEOUT)
                .onErrorReturn("DEGRADED");
    }

    private boolean isDown(String dependencyStatus) {
        return "DOWN".equalsIgnoreCase(dependencyStatus);
    }

    private boolean isDegraded(String dependencyStatus) {
        return "DEGRADED".equalsIgnoreCase(dependencyStatus) || "UNKNOWN".equalsIgnoreCase(dependencyStatus);
    }
}
