package com.arcana.cloud.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit Breaker configuration for gRPC service clients.
 *
 * <p>Prevents cascading failures when downstream services are unavailable.
 * Uses the Resilience4j library with configurable thresholds.</p>
 *
 * <p>Circuit states:</p>
 * <ul>
 *   <li>CLOSED: Normal operation, requests pass through</li>
 *   <li>OPEN: Service unavailable, requests fail fast</li>
 *   <li>HALF_OPEN: Testing if service recovered</li>
 * </ul>
 */
@Configuration
@ConditionalOnExpression(
    "'${communication.protocol:grpc}' == 'grpc' and '${deployment.layer:}' == 'controller'"
)
public class CircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    // Circuit Breaker Settings
    @Value("${circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${circuit-breaker.slow-call-rate-threshold:80}")
    private float slowCallRateThreshold;

    @Value("${circuit-breaker.slow-call-duration-threshold-ms:5000}")
    private long slowCallDurationThresholdMs;

    @Value("${circuit-breaker.wait-duration-in-open-state-ms:30000}")
    private long waitDurationInOpenStateMs;

    @Value("${circuit-breaker.permitted-calls-in-half-open-state:5}")
    private int permittedCallsInHalfOpenState;

    @Value("${circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${circuit-breaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    /**
     * Creates the Circuit Breaker registry with default configuration.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                // Percentage of failures to trip the circuit
                .failureRateThreshold(failureRateThreshold)
                // Percentage of slow calls to trip the circuit
                .slowCallRateThreshold(slowCallRateThreshold)
                // Duration threshold to consider a call slow
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThresholdMs))
                // How long to wait before transitioning from OPEN to HALF_OPEN
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                // Number of calls permitted in HALF_OPEN state
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                // Sliding window size for calculating failure rate
                .slidingWindowSize(slidingWindowSize)
                // Minimum calls before failure rate is calculated
                .minimumNumberOfCalls(minimumNumberOfCalls)
                // Use count-based sliding window
                .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                // Record these exceptions as failures
                .recordExceptions(
                    io.grpc.StatusRuntimeException.class,
                    java.util.concurrent.TimeoutException.class,
                    java.io.IOException.class
                )
                // Ignore these exceptions (don't count as failures)
                .ignoreExceptions(
                    com.arcana.cloud.exception.ResourceNotFoundException.class,
                    com.arcana.cloud.exception.ValidationException.class
                )
                // Automatically transition from OPEN to HALF_OPEN
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        log.info("Circuit Breaker configured: failureRate={}%, slowCallRate={}%, waitDuration={}ms",
            failureRateThreshold, slowCallRateThreshold, waitDurationInOpenStateMs);

        return registry;
    }

    /**
     * Circuit breaker for the User Service.
     */
    @Bean
    public CircuitBreaker userServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("userService");

        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                log.warn("User Service Circuit Breaker state changed: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
            .onCallNotPermitted(event ->
                log.warn("User Service call rejected by Circuit Breaker (circuit OPEN)"))
            .onError(event ->
                log.debug("User Service call failed: {}", event.getThrowable().getMessage()))
            .onSuccess(event ->
                log.trace("User Service call succeeded in {}ms", event.getElapsedDuration().toMillis()));

        return circuitBreaker;
    }

    /**
     * Circuit breaker for the Auth Service.
     */
    @Bean
    public CircuitBreaker authServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("authService");

        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                log.warn("Auth Service Circuit Breaker state changed: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
            .onCallNotPermitted(event ->
                log.warn("Auth Service call rejected by Circuit Breaker (circuit OPEN)"))
            .onError(event ->
                log.debug("Auth Service call failed: {}", event.getThrowable().getMessage()))
            .onSuccess(event ->
                log.trace("Auth Service call succeeded in {}ms", event.getElapsedDuration().toMillis()));

        return circuitBreaker;
    }

    /**
     * Circuit breaker for the Repository Service (service layer to repository layer).
     */
    @Bean
    @ConditionalOnExpression("'${deployment.layer:}' == 'service'")
    public CircuitBreaker repositoryServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("repositoryService");

        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                log.warn("Repository Service Circuit Breaker state changed: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));

        return circuitBreaker;
    }
}
