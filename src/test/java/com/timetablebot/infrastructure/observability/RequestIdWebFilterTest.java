package com.timetablebot.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestIdWebFilterTest {

    @Test
    void shouldReuseRequestIdFromIncomingHeader() {
        RequestIdWebFilter filter = new RequestIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/healthcheck")
                        .header(RequestIdWebFilter.REQUEST_ID_HEADER, "req-123")
                        .build()
        );

        WebFilterChain chain = webExchange -> {
            assertEquals("req-123", webExchange.getRequest().getHeaders().getFirst(RequestIdWebFilter.REQUEST_ID_HEADER));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("req-123", exchange.getResponse().getHeaders().getFirst(RequestIdWebFilter.REQUEST_ID_HEADER));
    }

    @Test
    void shouldGenerateRequestIdWhenIncomingHeaderMissing() {
        RequestIdWebFilter filter = new RequestIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healthcheck").build()
        );

        StepVerifier.create(filter.filter(exchange, webExchange -> Mono.empty()))
                .verifyComplete();

        String generated = exchange.getResponse().getHeaders().getFirst(RequestIdWebFilter.REQUEST_ID_HEADER);
        assertNotNull(generated);
    }
}
