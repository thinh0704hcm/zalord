package zalord.message_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public ObjectMapper objectMapper() {
        // findAndRegisterModules picks up jackson-datatype-jsr310. Disable
        // WRITE_DATES_AS_TIMESTAMPS so Instant serialises to ISO-8601 strings,
        // not epoch-nano numbers — Go's time.Time JSON unmarshal only accepts
        // RFC 3339 strings, so cross-language event consumers (chat-service Go)
        // need this. Without it, Java <-> Java works but the wire contract
        // silently breaks for any non-JVM consumer.
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    @Bean
    public OpenAPI messageServiceOpenAPI() {
        return new OpenAPI()
                // Same-origin via Kong → "Try it out" posts back to the gateway.
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Message Service API")
                        .description("Conversations and messages for the Zalord chat system. Routed through Kong; identity comes from the X-User-Id header that Kong injects from the verified JWT.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
