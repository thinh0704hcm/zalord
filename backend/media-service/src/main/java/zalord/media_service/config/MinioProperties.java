package zalord.media_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {

    /** http://minio:9000 — used by Spring code (server-side) for headObject etc. */
    private String internalEndpoint;
    /** http://localhost:9000 — baked into presigned URLs so the BROWSER can reach. */
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private final Bucket bucket = new Bucket();

    @Getter
    @Setter
    public static class Bucket {
        private String avatars;
        private String attachments;
    }
}
