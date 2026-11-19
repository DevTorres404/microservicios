package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health registry for PostgreSQL replicas.
 * Probes each replica with a 1000 ms timeout verifying SELECT 1 and pg_stat_wal_receiver.status == 'streaming'.
 * State machine:
 * - DOWN -> (1 probe OK) -> PROBING
 * - PROBING -> (2nd consecutive probe OK) -> UP
 * - Probe failure / immediate demotion -> DOWN
 * Publishes immutable snapshot of UP replicas and exposes replica_health gauges.
 */
public class ReplicaHealthRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReplicaHealthRegistry.class);

    private static final int PROBE_TIMEOUT_SECONDS = 1;
    private static final long PROBE_INTERVAL_MS = 5000;

    private final Function<DataSourceKey, HealthProbeResult> prober;
    private final MeterRegistry meterRegistry;
    private final Map<DataSourceKey, NodeHealth> nodes = new ConcurrentHashMap<>();
    private final AtomicReference<List<DataSourceKey>> healthySnapshot = new AtomicReference<>(Collections.emptyList());
    private ScheduledExecutorService scheduler;

    public ReplicaHealthRegistry(Function<DataSourceKey, HealthProbeResult> prober, MeterRegistry meterRegistry) {
        this.prober = prober;
        this.meterRegistry = meterRegistry;

        nodes.put(DataSourceKey.REPLICA_1, new NodeHealth(DataSourceKey.REPLICA_1));
        nodes.put(DataSourceKey.REPLICA_2, new NodeHealth(DataSourceKey.REPLICA_2));

        registerMetrics();
        updateHealthySnapshot();
    }

    public static ReplicaHealthRegistry forDataSources(Map<DataSourceKey, DataSource> dataSources, MeterRegistry meterRegistry) {
        return new ReplicaHealthRegistry(key -> {
            DataSource ds = dataSources.get(key);
            if (ds == null) {
                return HealthProbeResult.failure("datasource-not-configured", 0);
            }
            long start = System.currentTimeMillis();
            try (Connection conn = ds.getConnection()) {
                conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), PROBE_TIMEOUT_SECONDS * 1000);
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(PROBE_TIMEOUT_SECONDS);
                    stmt.executeQuery("SELECT 1");

                    try (ResultSet rs = stmt.executeQuery("SELECT status FROM pg_stat_wal_receiver")) {
                        if (rs.next()) {
                            String status = rs.getString("status");
                            if ("streaming".equalsIgnoreCase(status)) {
                                return HealthProbeResult.success(System.currentTimeMillis() - start);
                            }
                            return HealthProbeResult.failure("not-streaming: " + status, System.currentTimeMillis() - start);
                        } else {
                            return HealthProbeResult.failure("not-streaming: no receiver row", System.currentTimeMillis() - start);
                        }
                    }
                }
            } catch (Exception ex) {
                return HealthProbeResult.failure(ex.getMessage(), System.currentTimeMillis() - start);
            }
        }, meterRegistry);
    }

    public synchronized void startPeriodicProbing() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "replica-health-probe");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::probeNow, PROBE_INTERVAL_MS, PROBE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stopPeriodicProbing() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void close() {
        stopPeriodicProbing();
    }

    public void probeNow() {
        for (DataSourceKey key : List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2)) {
            probeNode(key);
        }
        updateHealthySnapshot();
    }

    private void probeNode(DataSourceKey key) {
        NodeHealth node = nodes.get(key);
        if (node == null) return;

        HealthProbeResult result;
        try {
            result = prober.apply(key);
        } catch (Exception ex) {
            result = HealthProbeResult.failure(ex.getMessage(), 0);
        }

        if (result != null && result.isSuccess()) {
            node.onSuccess();
        } else {
            String cause = (result != null && result.cause() != null) ? result.cause() : "probe-failed";
            node.onFailure(cause);
        }
    }

    public boolean isAvailable(DataSourceKey key) {
        NodeHealth node = nodes.get(key);
        return node != null && node.getState() == ReplicaHealthState.UP;
    }

    public ReplicaHealthState getState(DataSourceKey key) {
        NodeHealth node = nodes.get(key);
        return node != null ? node.getState() : ReplicaHealthState.DOWN;
    }

    public String getLastFailureCause(DataSourceKey key) {
        NodeHealth node = nodes.get(key);
        return node != null ? node.getLastFailureCause() : null;
    }

    public List<DataSourceKey> getHealthyReplicas() {
        return healthySnapshot.get();
    }

    public void markHealthy(DataSourceKey key) {
        NodeHealth node = nodes.get(key);
        if (node != null) {
            node.onSuccess();
            node.onSuccess();
            updateHealthySnapshot();
        }
    }

    public void markDown(DataSourceKey key, String cause) {
        NodeHealth node = nodes.get(key);
        if (node != null) {
            node.demote(cause);
            updateHealthySnapshot();
        }
    }

    private synchronized void updateHealthySnapshot() {
        List<DataSourceKey> healthy = new ArrayList<>();
        for (DataSourceKey key : List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2)) {
            if (isAvailable(key)) {
                healthy.add(key);
            }
        }
        healthySnapshot.set(Collections.unmodifiableList(healthy));
    }

    private void registerMetrics() {
        if (meterRegistry == null) return;

        for (DataSourceKey key : List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2)) {
            meterRegistry.gauge("replica_health",
                    List.of(io.micrometer.core.instrument.Tag.of("node", key.name())),
                    this,
                    reg -> reg.isAvailable(key) ? 1.0 : 0.0);
        }
    }

    private static class NodeHealth {
        private final DataSourceKey key;
        private ReplicaHealthState state = ReplicaHealthState.DOWN;
        private int consecutiveSuccesses = 0;
        private String lastFailureCause;

        NodeHealth(DataSourceKey key) {
            this.key = key;
        }

        synchronized void onSuccess() {
            consecutiveSuccesses++;
            if (state == ReplicaHealthState.DOWN) {
                state = ReplicaHealthState.PROBING;
                log.info("Node {} probe succeeded, moved from DOWN to PROBING (1/2 consecutive)", key);
            } else if (state == ReplicaHealthState.PROBING && consecutiveSuccesses >= 2) {
                state = ReplicaHealthState.UP;
                lastFailureCause = null;
                log.info("Node {} probe succeeded consecutively (2/2), moved to UP", key);
            }
        }

        synchronized void onFailure(String cause) {
            consecutiveSuccesses = 0;
            this.lastFailureCause = cause;
            if (state != ReplicaHealthState.DOWN) {
                log.warn("Node {} probe failed ({}), moved to DOWN", key, cause);
            }
            state = ReplicaHealthState.DOWN;
        }

        synchronized void demote(String cause) {
            consecutiveSuccesses = 0;
            this.lastFailureCause = cause;
            if (state != ReplicaHealthState.DOWN) {
                log.warn("Immediate demotion for node {} (cause: {}), moved to DOWN", key, cause);
            }
            state = ReplicaHealthState.DOWN;
        }

        synchronized ReplicaHealthState getState() {
            return state;
        }

        synchronized String getLastFailureCause() {
            return lastFailureCause;
        }
    }
}
