package zalord.message_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Dedupe lookup for DIRECT (1-1) conversations. pair_key is a deterministic
 * string built from the two user IDs sorted lexicographically, joined by '|'.
 * Ensures opening a chat with the same person twice returns the same conv.
 */
@Entity
@Getter
@Setter
@Table(name = "direct_lookup")
public class DirectLookup {

    @Id
    @Column(name = "pair_key", length = 80, updatable = false, nullable = false)
    private String pairKey;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /** Build the deterministic key for a (userA, userB) pair. */
    public static String pairKeyOf(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) < 0 ? sa + "|" + sb : sb + "|" + sa;
    }
}
