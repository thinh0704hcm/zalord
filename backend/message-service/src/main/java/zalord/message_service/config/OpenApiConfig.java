package zalord.message_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        // findAndRegisterModules picks up jackson-datatype-jsr310 from the
        // classpath so Instant/LocalDateTime serialize as ISO-8601 strings
        // instead of throwing. Required because MessageCreatedEvent uses Instant.
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
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
