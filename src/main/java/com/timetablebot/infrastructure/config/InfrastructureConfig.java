package com.timetablebot.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class InfrastructureConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());
    }

    @Bean(destroyMethod = "shutdown")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(t ->
                LoggerFactory.getLogger(InfrastructureConfig.class)
                        .error("Scheduler task error", t));
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public MongoClient mongoClient(
            @Value("${MONGODB_URI:mongodb://localhost:27017/timetable_bot}") String mongodbUri) {
        return MongoClients.create(mongodbUri);
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(
            MongoClient mongoClient,
            @Value("${MONGODB_URI:mongodb://localhost:27017/timetable_bot}") String mongodbUri) {
        return new ReactiveMongoTemplate(mongoClient, extractDatabaseName(mongodbUri));
    }

    @Bean
    public ConnectionFactory rabbitConnectionFactory(
            @Value("${RABBITMQ_HOST:localhost}") String host,
            @Value("${RABBITMQ_PORT:5672}") int port,
            @Value("${RABBITMQ_USERNAME:guest}") String username,
            @Value("${RABBITMQ_PASSWORD:guest}") String password) {
        CachingConnectionFactory cf = new CachingConnectionFactory(host, port);
        cf.setUsername(username);
        cf.setPassword(password);
        return cf;
    }

    private String extractDatabaseName(String mongodbUri) {
        int slashIndex = mongodbUri.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == mongodbUri.length() - 1) return "timetable_bot";
        String tail = mongodbUri.substring(slashIndex + 1);
        int queryIndex = tail.indexOf('?');
        return (queryIndex >= 0 ? tail.substring(0, queryIndex) : tail).trim();
    }
}
