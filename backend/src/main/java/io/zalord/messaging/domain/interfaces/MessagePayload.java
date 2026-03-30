package io.zalord.messaging.domain.interfaces;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessagePayload.TextPayload.class, name = "TEXT"),
    @JsonSubTypes.Type(value = MessagePayload.ImagePayload.class, name = "IMAGE"),
    @JsonSubTypes.Type(value = MessagePayload.VideoPayload.class, name = "VIDEO"),
    @JsonSubTypes.Type(value = MessagePayload.FilePayload.class,  name = "FILE")
})
public sealed interface MessagePayload {

    record TextPayload(String text) implements MessagePayload {}

    record ImagePayload(
        String mimeType,
        String fileName,
        Long fileSizeBytes,
        int width,
        int height,
        String thumbnailUrl
    ) implements MessagePayload {}

    record VideoPayload(
        String mimeType,
        String fileName,
        Long fileSizeBytes,
        int width,
        int height,
        int durationSeconds,
        String thumbnailUrl
    ) implements MessagePayload {}

    record FilePayload(
        String mimeType,
        String fileName,
        Long fileSizeBytes
    ) implements MessagePayload {}
}