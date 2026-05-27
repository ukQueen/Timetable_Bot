package com.timetablebot.application.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Long messageId, Chat chat, User from, String text, Document document) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(Long id, String username, String firstName, String lastName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("mime_type") String mimeType
    ) {
    }
}
