package zalord.auth_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly bind Resilience4j circuit-breaker gauges into the Micrometer
 * registry — Spring Boot 4 + resilience4j-spring-boot3 don't auto-wire it
 * in our setup, so we register the binder by hand.
 */
@Configuration
public class MetricsConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    public MetricsConfig(CircuitBreakerRegistry circuitBreakerRegistry,
                         MeterRegistry meterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void bind() {
        TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);
    }
}
