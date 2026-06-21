package zalord.media_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Ensures the avatars + attachments buckets exist on startup. Idempotent —
 * BucketAlreadyOwnedByYouException is treated as success.
 *
 * Avatars: kept private at the bucket level, but we hand out presigned GET
 * URLs without membership checks. Attachments: private + membership-gated.
 */
@Component
@Slf4j
public class BucketInitializer implements CommandLineRunner {

    private final S3Client s3;
    private final MinioProperties props;

    public BucketInitializer(S3Client s3, MinioProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        ensureBucket(props.getBucket().getAvatars());
        ensureBucket(props.getBucket().getAttachments());
    }

    private void ensureBucket(String name) {
        try {
            s3.headBucket(b -> b.bucket(name));
            log.info("Bucket exists: {}", name);
        } catch (NoSuchBucketException notFound) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(name).build());
                log.info("Bucket created: {}", name);
            } catch (BucketAlreadyOwnedByYouException ignore) {
                log.info("Bucket already owned: {}", name);
            }
        }
    }
}
