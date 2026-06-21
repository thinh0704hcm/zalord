package zalord.message_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Join row linking a message to one media. media_id is NOT a FK — media-service
 * owns its own DB; ownership/state is validated synchronously via gRPC before
 * the row is inserted.
 */
@Entity
@Table(name = "message_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {

    @EmbeddedId
    private Id id;

    @Column(name = "position", nullable = false)
    private short position;

    public MessageAttachment(UUID messageId, UUID mediaId, short position) {
        this.id = new Id(messageId, mediaId);
        this.position = position;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "message_id", nullable = false)
        private UUID messageId;

        @Column(name = "media_id", nullable = false)
        private UUID mediaId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(messageId, other.messageId) && Objects.equals(mediaId, other.mediaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, mediaId);
        }
    }
}
