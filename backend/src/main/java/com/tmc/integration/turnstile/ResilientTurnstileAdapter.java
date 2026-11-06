package com.tmc.integration.turnstile;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Decorator that adds a hard timeout and a circuit breaker to any
 * {@link TurnstilePort} (ADR-009). Because it implements the same port it is a
 * drop-in substitute for the delegate (Liskov), and adding it required no change
 * to the attendance module.
 */
public class ResilientTurnstileAdapter implements TurnstilePort, AutoCloseable {

    private final TurnstilePort delegate;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration timeout;

    public ResilientTurnstileAdapter(TurnstilePort delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        this.circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("turnstile");
    }

    @Override
    public GateOutcome open(String gateId) {
        long start = System.nanoTime();
        try {
            GateOutcome outcome = circuitBreaker.executeSupplier(() -> callWithTimeout(gateId));
            return new GateOutcome(outcome.opened(), outcome.detail(),
                    (System.nanoTime() - start) / 1_000_000);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            throw new TurnstileUnavailableException("Circuit breaker is open; gate unavailable");
        }
    }

    private GateOutcome callWithTimeout(String gateId) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> delegate.open(gateId), executor)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TurnstileUnavailableException(
                    "Turnstile did not respond within " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new TurnstileUnavailableException(cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TurnstileUnavailableException("Interrupted while waiting for the turnstile");
        }
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
