package zalord.message_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly bind Resilience4j circuit-breaker gauges into the Micrometer
 * registry. Spring Boot 4 + resilience4j-spring-boot3 don't auto-wire this
 * binding in our setup, so /actuator/prometheus stays empty of
 * resilience4j_circuitbreaker_* series unless we register it ourselves.
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
