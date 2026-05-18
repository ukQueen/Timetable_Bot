package com.timetablebot.integration;

import com.timetablebot.TimetableBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import com.timetablebot.application.schedule.ScheduleModule;
import com.timetablebot.infrastructure.schedule.ImportHistoryRepository;
import com.timetablebot.infrastructure.schedule.ScheduleEventRepository;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = TimetableBotApplication.class)
class InfrastructureIntegrationTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("telegram.bot.enabled", () -> "false");
        registry.add("telegram.bot.token", () -> "");
    }

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private ScheduleModule scheduleModule;

    @Autowired
    private ScheduleEventRepository scheduleEventRepository;

    @Autowired
    private ImportHistoryRepository importHistoryRepository;

    @Test
    void shouldConnectToMongoAndRabbit() {
        StepVerifier.create(
                        mongoTemplate.executeCommand("{ ping: 1 }")
                                .map(doc -> doc.getDouble("ok") != null && doc.getDouble("ok") >= 1.0)
                )
                .expectNext(true)
                .verifyComplete();

        Connection connection = connectionFactory.createConnection();
        assertNotNull(connection);
        assertTrue(connection.isOpen());
        connection.close();
    }
    @Test
    void shouldImportCsvAndWriteHistory() {
        String userId = "u-it-1";
        String csv = "TYPE,TITLE,PLACE,START_ISO,END_ISO\n" +
                "LESSON,Math,A-101,2026-05-20T10:00:00Z,2026-05-20T11:00:00Z";

        StepVerifier.create(scheduleModule.importFromCsv(userId, csv))
                .expectNext(1)
                .verifyComplete();

        StepVerifier.create(scheduleEventRepository.countByUserId(userId))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(importHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).next())
                .assertNext(item -> {
                    assertTrue("csv".equals(item.getSource()));
                    assertTrue(item.getImportedCount() == 1);
                })
                .verifyComplete();
    }

}
