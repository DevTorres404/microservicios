package com.example.microservicios.persistence.routing;

/**
 * Immutable record capturing a single routing decision and its transaction/read semantics.
 */
public record RoutingDecision(DataSourceKey key, boolean txReadOnly, boolean criticalRead) {
}
