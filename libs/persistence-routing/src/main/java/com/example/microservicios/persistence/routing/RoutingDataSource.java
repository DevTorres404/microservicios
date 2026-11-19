package com.example.microservicios.persistence.routing;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Dynamic routing DataSource mapping DataSourceKey to physical Hikari pools.
 * Default target data source is PRIMARY.
 * lenientFallback is explicitly set to false so that unknown keys fail noisily (IllegalStateException)
 * instead of silently drifting to PRIMARY.
 * When a connection attempt to a node fails, immediate demotion occurs (markDown) and
 * DbNodeUnavailableException (503 DB_NODE_UNAVAILABLE) is raised without retrying within the transaction.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    private final ReplicaHealthRegistry healthRegistry;

    public RoutingDataSource(DataSource primary, DataSource replica1, DataSource replica2) {
        this(primary, replica1, replica2, null);
    }

    public RoutingDataSource(DataSource primary, DataSource replica1, DataSource replica2, ReplicaHealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceKey.PRIMARY, primary);
        targetDataSources.put(DataSourceKey.REPLICA_1, replica1);
        targetDataSources.put(DataSourceKey.REPLICA_2, replica2);

        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(primary);
        setLenientFallback(false);
        afterPropertiesSet();
    }

    public RoutingDataSource(DataSource primary, Map<Object, Object> targetDataSources) {
        this.healthRegistry = null;
        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(primary);
        setLenientFallback(false);
        afterPropertiesSet();
    }

    @Override
    public Object determineCurrentLookupKey() {
        DataSourceKey key = DataSourceContextHolder.peek();
        return key != null ? key : DataSourceKey.PRIMARY;
    }

    @Override
    public DataSource determineTargetDataSource() {
        return super.determineTargetDataSource();
    }

    @Override
    public Connection getConnection() throws SQLException {
        Object lookupKey = determineCurrentLookupKey();
        DataSource target = determineTargetDataSource();
        try {
            return target.getConnection();
        } catch (Exception ex) {
            handleConnectionFailure(lookupKey, ex);
            throw ex;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Object lookupKey = determineCurrentLookupKey();
        DataSource target = determineTargetDataSource();
        try {
            return target.getConnection(username, password);
        } catch (Exception ex) {
            handleConnectionFailure(lookupKey, ex);
            throw ex;
        }
    }

    private void handleConnectionFailure(Object lookupKey, Exception ex) {
        if (lookupKey instanceof DataSourceKey key && key != DataSourceKey.PRIMARY && healthRegistry != null) {
            healthRegistry.markDown(key, "connection-refused");
        }
        throw new DbNodeUnavailableException("Database node unavailable: " + lookupKey + " (" + ex.getMessage() + ")", ex);
    }
}
