package zalord.auth_service.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zalord.user.v1.CreateProfileRequest;
import zalord.user.v1.UserInternalGrpc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Thin gRPC client wrapper for user-service's UserInternal API.
 * Sync replacement for the previous user.created RabbitMQ event.
 */
@Component
@Slf4j
public class UserGrpcClient {

    @Value("${zalord.user-service.grpc-target:user-service:9082}")
    private String target;

    private ManagedChannel channel;
    private UserInternalGrpc.UserInternalBlockingStub stub;

    @PostConstruct
    public void init() {
        // usePlaintext: internal traffic on the docker net; no TLS needed in dev.
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        stub = UserInternalGrpc.newBlockingStub(channel);
        log.info("UserGrpcClient → {}", target);
    }

    public void createProfile(UUID userId, String displayName, String phoneNumber) {
        try {
            stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .createProfile(CreateProfileRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setDisplayName(displayName)
                            .setPhoneNumber(phoneNumber)
                            .build());
        } catch (StatusRuntimeException ex) {
            // Surface gRPC error so the calling transaction rolls back the
            // auth-side insert. Strong consistency: if user-service can't
            // create the profile, register fails as a whole.
            throw new RuntimeException("user-service CreateProfile failed: " + ex.getStatus(), ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
