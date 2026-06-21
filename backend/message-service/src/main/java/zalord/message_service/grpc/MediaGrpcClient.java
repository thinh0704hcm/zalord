package zalord.message_service.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zalord.media.v1.InvalidAttachment;
import zalord.media.v1.MediaInternalGrpc;
import zalord.media.v1.ValidateAttachmentsRequest;
import zalord.media.v1.ValidateAttachmentsResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sync client into media-service's ValidateAttachments RPC. Used on the
 * send-message hot path to reject attachments before persisting the message.
 * Deadline is short (3s) because the message POST is user-facing.
 */
@Component
@Slf4j
public class MediaGrpcClient {

    private final String target;
    private ManagedChannel channel;
    private MediaInternalGrpc.MediaInternalBlockingStub stub;

    public MediaGrpcClient(@Value("${zalord.media-service.grpc.target:media-service:9086}") String target) {
        this.target = target;
    }

    @PostConstruct
    void init() {
        channel = NettyChannelBuilder.forTarget(target)
                .negotiationType(NegotiationType.PLAINTEXT)
                .build();
        stub = MediaInternalGrpc.newBlockingStub(channel);
        log.info("MediaGrpcClient target={}", target);
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        if (channel != null) channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    }

    public Result validate(UUID caller, UUID conversationId, List<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Result.ok();

        ValidateAttachmentsRequest.Builder req = ValidateAttachmentsRequest.newBuilder()
                .setCallerUserId(caller.toString())
                .setConversationId(conversationId.toString());
        for (UUID id : mediaIds) req.addMediaId(id.toString());

        ValidateAttachmentsResponse resp = stub
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .validateAttachments(req.build());

        if (resp.getValid()) return Result.ok();
        return Result.invalid(resp.getInvalidList());
    }

    public record Result(boolean valid, List<InvalidAttachment> invalid) {
        public static Result ok() { return new Result(true, List.of()); }
        public static Result invalid(List<InvalidAttachment> bad) { return new Result(false, bad); }
    }
}
