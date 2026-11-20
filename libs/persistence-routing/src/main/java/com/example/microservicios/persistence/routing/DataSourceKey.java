package com.example.microservicios.persistence.routing;

/**
 * Closed vocabulary of PostgreSQL nodes reachable through the routing data source.
 *
 * <p>The set is intentionally fixed: one authoritative primary and exactly two asynchronous
 * replicas. The name of each constant is a stable contract because it travels in the
 * {@code X-Data-Source} header and in the structured routing log line.</p>
 */
public enum DataSourceKey {

    /** Authoritative node. Every write and every critical read lands here. */
    PRIMARY,

    /** First asynchronous replica. Ordinary reads only. */
    REPLICA_1,

    /** Second asynchronous replica. Ordinary reads only. */
    REPLICA_2
}
