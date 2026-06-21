package zalord.media_service.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zalord.media.v1.InvalidAttachment;
import zalord.media.v1.MediaInternalGrpc;
import zalord.media.v1.ValidateAttachmentsRequest;
import zalord.media.v1.ValidateAttachmentsResponse;
import zalord.media_service.enums.MediaKind;
import zalord.media_service.enums.MediaStatus;
import zalord.media_service.model.Media;
import zalord.media_service.repository.MediaRepository;
import zalord.media_service.service.MediaCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal gRPC server — exposes ValidateAttachments to message-service so a
 * SendMessage call can reject bogus attachment ids before the message is
 * persisted (and before any event fans out). Lives on a port distinct from
 * the public REST port; not routed through Kong.
 */
@Component
@Slf4j
public class MediaInternalServer {

    private final MediaRepository mediaRepo;
    private final MediaCache cache;
    private final int port;
    private Server server;

    public MediaInternalServer(MediaRepository mediaRepo,
                               MediaCache cache,
                               @Value("${zalord.grpc.port:9086}") int port) {
        this.mediaRepo = mediaRepo;
        this.cache = cache;
        this.port = port;
    }

    @PostConstruct
    void start() throws Exception {
        server = NettyServerBuilder.forPort(port)
                .addService(new ServiceImpl())
                .build()
                .start();
        log.info("media gRPC server listening on :{}", port);
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.shutdown();
            log.info("media gRPC server stopped");
        }
    }

    private class ServiceImpl extends MediaInternalGrpc.MediaInternalImplBase {

        @Override
        public void validateAttachments(ValidateAttachmentsRequest req,
                                        StreamObserver<ValidateAttachmentsResponse> resp) {
            try {
                ValidateAttachmentsResponse out = doValidate(req);
                resp.onNext(out);
                resp.onCompleted();
            } catch (Exception ex) {
                log.error("validateAttachments failed", ex);
                resp.onError(io.grpc.Status.INTERNAL
                        .withDescription(ex.getMessage())
                        .asRuntimeException());
            }
        }

        private ValidateAttachmentsResponse doValidate(ValidateAttachmentsRequest req) {
            ValidateAttachmentsResponse.Builder builder = ValidateAttachmentsResponse.newBuilder();
            if (req.getMediaIdCount() == 0) {
                return builder.setValid(true).build();
            }
            UUID caller, conversation;
            try {
                caller = UUID.fromString(req.getCallerUserId());
                conversation = UUID.fromString(req.getConversationId());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("caller_user_id and conversation_id must be UUIDs");
            }

            List<UUID> requested = new ArrayList<>(req.getMediaIdCount());
            List<InvalidAttachment> invalid = new ArrayList<>();
            for (String raw : req.getMediaIdList()) {
                try {
                    requested.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ex) {
                    invalid.add(InvalidAttachment.newBuilder()
                            .setMediaId(raw).setReason("MALFORMED_ID").build());
                }
            }

            // Read-through cache: hit Redis first, fall back to Postgres for misses,
            // then populate the cache so subsequent validates skip the DB entirely.
            Map<UUID, MediaCache.Snapshot> cached = cache.getMany(requested);
            List<UUID> misses = requested.stream().filter(id -> !cached.containsKey(id)).toList();
            Map<UUID, MediaCache.Snapshot> all = new HashMap<>(cached);
            if (!misses.isEmpty()) {
                List<MediaCache.Snapshot> loaded = mediaRepo.findByIdIn(misses).stream()
                        .map(MediaCache.Snapshot::of)
                        .toList();
                cache.putAll(loaded);
                loaded.forEach(s -> all.put(s.id(), s));
            }
            log.debug("ValidateAttachments cache hit={}/{} miss={}",
                    cached.size(), requested.size(), misses.size());

            for (UUID id : requested) {
                String reason = checkOne(all.get(id), caller, conversation);
                if (reason != null) {
                    invalid.add(InvalidAttachment.newBuilder()
                            .setMediaId(id.toString()).setReason(reason).build());
                }
            }

            return builder
                    .setValid(invalid.isEmpty())
                    .addAllInvalid(invalid)
                    .build();
        }

        private String checkOne(MediaCache.Snapshot m, UUID caller, UUID conversation) {
            if (m == null) return "NOT_FOUND";
            if (MediaStatus.DELETED.name().equals(m.status())) return "DELETED";
            if (!MediaKind.ATTACHMENT.name().equals(m.kind())) return "NOT_ATTACHMENT";
            if (!caller.equals(m.ownerId())) return "NOT_OWNED";
            if (m.conversationId() == null || !conversation.equals(m.conversationId())) {
                return "WRONG_CONVERSATION";
            }
            if (!MediaStatus.READY.name().equals(m.status())) return "NOT_READY";
            return null;
        }
    }
}
