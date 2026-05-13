package com.timetablebot.application.telegram.dto;

public record BotMessageResponse(String status, String message) {
    public static BotMessageResponse ok(String message) {
        return new BotMessageResponse("ok", message);
    }

    public static BotMessageResponse error(String message) {
        return new BotMessageResponse("error", message);
    }
}
