package com.timetablebot;

import com.timetablebot.infrastructure.config.DotenvPropertyLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class TimetableBotApplication {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        DotenvPropertyLoader.loadFromProjectRoot();

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();

        int port = resolvePort();
        DisposableServer server = HttpServer.create()
                .host("0.0.0.0")
                .port(port)
                .handle(new ReactorHttpHandlerAdapter(httpHandler))
                .bindNow();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.disposeNow();
            context.close();
        }));

        server.onDispose().block();
    }

    private static int resolvePort() {
        String rawPort = firstNonBlank(
                System.getProperty("SERVER_PORT"),
                System.getenv("SERVER_PORT"),
                System.getProperty("server.port"),
                System.getenv("server.port"),
                String.valueOf(DEFAULT_PORT)
        );
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return String.valueOf(DEFAULT_PORT);
    }
}
