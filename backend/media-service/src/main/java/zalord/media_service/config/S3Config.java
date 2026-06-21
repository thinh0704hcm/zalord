package zalord.media_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Two S3 clients:
 *   - s3Client      → uses INTERNAL endpoint (minio:9000) for server-side ops
 *                     like createBucket, headObject. Runs inside docker net.
 *   - s3Presigner   → uses PUBLIC endpoint (localhost:9000) so the URLs we
 *                     hand to clients can actually be reached by the browser.
 *
 * Both use forcePathStyle = true. MinIO does not support virtual-hosted style
 * (which would put the bucket in the hostname) for arbitrary hostnames.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(MinioProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getInternalEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .region(Region.US_EAST_1)                       // arbitrary for MinIO
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(MinioProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getPublicEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
