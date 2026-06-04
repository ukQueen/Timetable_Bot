package com.timetablebot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.timetablebot.api.admin.AdminAuthController;
import com.timetablebot.api.admin.AdminUsersController;
import com.timetablebot.application.security.AccessPolicy;
import com.timetablebot.domain.user.OnboardingStatus;
import com.timetablebot.infrastructure.observability.RequestIdWebFilter;
import com.timetablebot.infrastructure.security.AdminAuthProperties;
import com.timetablebot.infrastructure.security.AdminAuthWebFilter;
import com.timetablebot.infrastructure.user.UserDocument;
import com.timetablebot.infrastructure.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;

@ExtendWith(RestDocumentationExtension.class)
class RestDocsTest {

    private WebTestClient webTestClient;
    private static DisposableServer server;
    private static int port;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        if (server == null) {
            AnnotationConfigApplicationContext context =
                    new AnnotationConfigApplicationContext(TestConfig.class);
            HttpHandler handler = WebHttpHandlerBuilder.applicationContext(context).build();
            server = HttpServer.create().port(0)
                    .handle(new ReactorHttpHandlerAdapter(handler))
                    .bindNow();
            port = server.port();
        }

        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .filter(WebTestClientRestDocumentation.documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void healthcheck() {
        webTestClient.get().uri("/healthcheck")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(document("healthcheck",
                        responseFields(
                                fieldWithPath("status").description("Общий статус: `UP`, `DEGRADED` или `DOWN`"),
                                fieldWithPath("service").description("Название сервиса"),
                                fieldWithPath("timestamp").description("Время проверки в формате ISO-8601"),
                                fieldWithPath("request_id").description("Корреляционный идентификатор запроса"),
                                subsectionWithPath("dependencies").description("Статусы зависимостей: mongodb, rabbitmq, telegram"),
                                fieldWithPath("authors").description("Список авторов проекта")
                        )));
    }

    @Test
    void adminAuth_success() {
        webTestClient.post().uri("/admin/auth")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(document("admin-auth-success",
                        responseFields(
                                fieldWithPath("token").description("Bearer токен для доступа к административным эндпоинтам"),
                                fieldWithPath("type").description("Тип токена: всегда `Bearer`")
                        )));
    }

    @Test
    void adminAuth_unauthorized() {
        webTestClient.post().uri("/admin/auth")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"wrong\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .consumeWith(document("admin-auth-unauthorized"));
    }

    @Test
    void adminUsers_success() {
        webTestClient.get().uri("/admin/users")
                .header("Authorization", "Bearer dev-admin-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(document("admin-users-success",
                        requestHeaders(
                                headerWithName("Authorization").description("Bearer токен администратора. Формат: `Bearer <token>`")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Telegram chat ID пользователя"),
                                fieldWithPath("[].timezone").description("Часовой пояс пользователя"),
                                fieldWithPath("[].onboardingStatus").description("Статус онбординга: `NEW` или `COMPLETED`"),
                                fieldWithPath("[].createdAt").description("Дата регистрации (ISO-8601)"),
                                fieldWithPath("[].updatedAt").description("Дата последнего обновления (ISO-8601)")
                        )));
    }

    @Test
    void adminUsers_unauthorized() {
        webTestClient.get().uri("/admin/users")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .consumeWith(document("admin-users-unauthorized"));
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
            return new HealthcheckController(
                    emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider(),
                    "TestAuthor1,TestAuthor2"
            );
        }

        @SuppressWarnings("unchecked")
        private <T> ObjectProvider<T> emptyProvider() {
            return new ObjectProvider<>() {
                @Override public T getIfAvailable() { return null; }
            };
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
                    doc.setOnboardingStatus(OnboardingStatus.COMPLETED);
                    doc.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
                    doc.setUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));
                    return Flux.just(doc);
                }

                @Override public <S extends UserDocument> Mono<S> save(S e) { return Mono.just(e); }
                @Override public <S extends UserDocument> Flux<S> saveAll(Iterable<S> e) { return Flux.empty(); }
                @Override public <S extends UserDocument> Flux<S> saveAll(org.reactivestreams.Publisher<S> e) { return Flux.empty(); }
                @Override public Mono<UserDocument> findById(String id) { return Mono.empty(); }
                @Override public Mono<UserDocument> findById(org.reactivestreams.Publisher<String> id) { return Mono.empty(); }
                @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
                @Override public Mono<Boolean> existsById(org.reactivestreams.Publisher<String> id) { return Mono.just(false); }
                @Override public Flux<UserDocument> findAllById(Iterable<String> ids) { return Flux.empty(); }
                @Override public Flux<UserDocument> findAllById(org.reactivestreams.Publisher<String> ids) { return Flux.empty(); }
                @Override public Mono<Long> count() { return Mono.just(1L); }
                @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
                @Override public Mono<Void> deleteById(org.reactivestreams.Publisher<String> id) { return Mono.empty(); }
                @Override public Mono<Void> delete(UserDocument e) { return Mono.empty(); }
                @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll(Iterable<? extends UserDocument> e) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll(org.reactivestreams.Publisher<? extends UserDocument> e) { return Mono.empty(); }
                @Override public Mono<Void> deleteAll() { return Mono.empty(); }
            };
        }

        @Bean
        AdminUsersController adminUsersController(AccessPolicy accessPolicy, UserRepository userRepository) {
            return new AdminUsersController(accessPolicy, userRepository);
        }
    }
}
