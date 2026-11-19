package com.example.microservicios.persistence.routing;

/**
 * Health states for replica nodes in PostgreSQL replication cluster.
 * Transitions:
 * DOWN -> (1 successful probe) -> PROBING
 * PROBING -> (2nd consecutive successful probe) -> UP
 * Any failure / demotion -> DOWN
 */
public enum ReplicaHealthState {
    DOWN,
    PROBING,
    UP
}
