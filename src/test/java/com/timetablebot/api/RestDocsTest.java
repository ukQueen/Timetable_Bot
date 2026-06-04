package com.timetablebot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.timetablebot.api.admin.AdminAuthController;
import com.timetablebot.api.admin.AdminUsersController;
import com.timetablebot.application.security.AccessPolicy;
import com.timetablebot.infrastructure.observability.RequestIdWebFilter;
import com.timetablebot.infrastructure.security.AdminAuthProperties;
import com.timetablebot.infrastructure.security.AdminAuthWebFilter;
import com.timetablebot.infrastructure.user.UserDocument;
import com.timetablebot.infrastructure.user.UserRepository;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.restdocs.ManualRestDocumentation;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.documentationConfiguration;

class RestDocsTest {

    private static DisposableServer server;
    private static int port;

    private final ManualRestDocumentation restDocumentation = new ManualRestDocumentation();
    private RequestSpecification spec;

    @BeforeAll
    static void startServer() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class);
        HttpHandler handler = WebHttpHandlerBuilder.applicationContext(context).build();
        server = HttpServer.create().port(0)
                .handle(new ReactorHttpHandlerAdapter(handler))
                .bindNow();
        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        server.disposeNow();
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        this.restDocumentation.beforeTest(getClass(), testInfo.getTestMethod().get().getName());
        this.spec = new RequestSpecBuilder()
                .addFilter(documentationConfiguration(restDocumentation))
                .build();
    }

    @AfterEach
    void tearDown() {
        this.restDocumentation.afterTest();
    }

    @Test
    void healthcheck() {
        given(this.spec)
                .filter(document("healthcheck",
                        responseFields(
                                fieldWithPath("status").description("Общий статус сервиса: `UP`, `DEGRADED` или `DOWN`"),
                                fieldWithPath("service").description("Название сервиса"),
                                fieldWithPath("timestamp").description("Время проверки в формате ISO-8601"),
                                fieldWithPath("request_id").description("Корреляционный идентификатор запроса"),
                                subsectionWithPath("dependencies").description("Статусы зависимостей: mongodb, rabbitmq, telegram"),
                                fieldWithPath("authors").description("Список авторов проекта")
                        )))
                .when().get("http://localhost:" + port + "/healthcheck")
                .then()
                .statusCode(200)
                .body("$", hasKey("status"))
                .body("$", hasKey("dependencies"));
    }

    @Test
    void adminAuth_success() {
        given(this.spec)
                .contentType("application/json")
                .body("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .filter(document("admin-auth-success",
                        responseFields(
                                fieldWithPath("token").description("Bearer токен для доступа к административным эндпоинтам"),
                                fieldWithPath("type").description("Тип токена: всегда `Bearer`")
                        )))
                .when().post("http://localhost:" + port + "/admin/auth")
                .then()
                .statusCode(200)
                .body("type", equalTo("Bearer"));
    }

    @Test
    void adminAuth_unauthorized() {
        given(this.spec)
                .contentType("application/json")
                .body("{\"username\":\"wrong\",\"password\":\"wrong\"}")
                .filter(document("admin-auth-unauthorized"))
                .when().post("http://localhost:" + port + "/admin/auth")
                .then()
                .statusCode(401);
    }

    @Test
    void adminUsers_success() {
        given(this.spec)
                .header("Authorization", "Bearer dev-admin-token")
                .filter(document("admin-users-success",
                        requestHeaders(
                                headerWithName("Authorization").description("Bearer токен администратора. Формат: `Bearer <token>`")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Telegram chat ID пользователя"),
                                fieldWithPath("[].timezone").description("Часовой пояс пользователя"),
                                fieldWithPath("[].onboardingStatus").description("Статус онбординга: `NEW` или `COMPLETED`"),
                                fieldWithPath("[].createdAt").description("Дата и время регистрации (ISO-8601)"),
                                fieldWithPath("[].updatedAt").description("Дата и время последнего обновления (ISO-8601)")
                        )))
                .when().get("http://localhost:" + port + "/admin/users")
                .then()
                .statusCode(200);
    }

    @Test
    void adminUsers_unauthorized() {
        given(this.spec)
                .filter(document("admin-users-unauthorized"))
                .when().get("http://localhost:" + port + "/admin/users")
                .then()
                .statusCode(401);
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        AdminAuthProperties adminAuthProperties() {
            return new AdminAuthProperties("admin", "admin123", "dev-admin-token");
        }

        @Bean
        AdminAuthWebFilter adminAuthWebFilter(AdminAuthProperties properties) {
            return new AdminAuthWebFilter(properties);
        }

        @Bean
        RequestIdWebFilter requestIdWebFilter() {
            return new RequestIdWebFilter();
        }

        @Bean
        AccessPolicy accessPolicy() {
            return new AccessPolicy();
        }

        @Bean
        HealthcheckController healthcheckController() {
            ObjectProvider<ReactiveMongoTemplate> mongoProvider = new ObjectProvider<>() {
                @Override public ReactiveMongoTemplate getIfAvailable() { return null; }
            };
            ObjectProvider<org.springframework.amqp.rabbit.connection.ConnectionFactory> rabbitProvider =
                    new ObjectProvider<>() {
                        @Override public org.springframework.amqp.rabbit.connection.ConnectionFactory getIfAvailable() { return null; }
                    };
            ObjectProvider<com.timetablebot.infrastructure.telegram.TelegramBotProperties> telegramPropsProvider =
                    new ObjectProvider<>() {
                        @Override public com.timetablebot.infrastructure.telegram.TelegramBotProperties getIfAvailable() { return null; }
                    };
            ObjectProvider<com.timetablebot.infrastructure.telegram.TelegramBotClient> telegramClientProvider =
                    new ObjectProvider<>() {
                        @Override public com.timetablebot.infrastructure.telegram.TelegramBotClient getIfAvailable() { return null; }
                    };
            return new HealthcheckController(
                    mongoProvider, rabbitProvider, telegramPropsProvider, telegramClientProvider,
                    "TestAuthor1,TestAuthor2"
            );
        }

        @Bean
        AdminAuthController adminAuthController(AdminAuthProperties properties) {
            return new AdminAuthController(properties);
        }

        @Bean
        UserRepository userRepository() {
            return new UserRepository() {
                @Override
                public Flux<UserDocument> findAll() {
                    UserDocument doc = new UserDocument();
                    doc.setId("123456789");
                    doc.setTimezone("Europe/Moscow");
                    doc.setOnboardingStatus(com.timetablebot.domain.user.OnboardingStatus.COMPLETED);
                    doc.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
                    doc.setUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));
                    return Flux.just(doc);
                }

                @Override public <S extends UserDocument> Mono<S> save(S entity) { return Mono.just(entity); }
                @Override public Mono<UserDocument> findById(String id) { return Mono.empty(); }
                @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
                @Override public Flux<UserDocument> findAllById(Iterable<String> ids) { return Flux.empty(); }
                @Override public Flux<UserDocument> findAllById(org.reactivestreams.Publisher<String> ids) { return Flux.empty(); }
                @Override public Mono<Long> count() { return Mono.just(1L); }
                @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
                @Override public Mono<Void> delete(UserDocument entity) { return Mono.empty(); }
                @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll(Iterable<? extends UserDocument> entities) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll(org.reactivestreams.Publisher<? extends UserDocument> entityStream) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll() { return Mono.empty(); }
                @Override public <S extends UserDocument> Flux<S> saveAll(Iterable<S> entities) { return Flux.empty(); }
                @Override public <S extends UserDocument> Flux<S> saveAll(org.reactivestreams.Publisher<S> entityStream) { return Flux.empty(); }
                @Override public Mono<UserDocument> findById(org.reactivestreams.Publisher<String> id) { return Mono.empty(); }
                @Override public Mono<Boolean> existsById(org.reactivestreams.Publisher<String> id) { return Mono.just(false); }
                @Override public Mono<Void> deleteById(org.reactivestreams.Publisher<String> id) { return Mono.empty(); }
            };
        }

        @Bean
        AdminUsersController adminUsersController(AccessPolicy accessPolicy, UserRepository userRepository) {
            return new AdminUsersController(accessPolicy, userRepository);
        }
    }
}
