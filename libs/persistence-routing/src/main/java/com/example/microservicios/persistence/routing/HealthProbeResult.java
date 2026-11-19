package com.example.microservicios.persistence.routing;

/**
 * Result of a single health probe to a replica node.
 */
public record HealthProbeResult(boolean isSuccess, String cause, long durationMs) {

    public static HealthProbeResult success(long durationMs) {
        return new HealthProbeResult(true, null, durationMs);
    }

    public static HealthProbeResult failure(String cause, long durationMs) {
        return new HealthProbeResult(false, cause, durationMs);
    }
}
