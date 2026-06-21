package zalord.group_service.config;

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
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);   // ISO-8601 strings for cross-lang consumers
        return m;
    }

    @Bean
    public OpenAPI groupServiceOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Group Service API")
                        .description("Group conversations management for the Zalord chat system. Group IDs are reused as conversation IDs in message-service.")
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
