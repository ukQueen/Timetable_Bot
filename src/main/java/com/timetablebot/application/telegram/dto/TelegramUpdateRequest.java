package com.timetablebot.application.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Long messageId, Chat chat, User from, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(Long id, String username, String firstName, String lastName) {
    }
}
