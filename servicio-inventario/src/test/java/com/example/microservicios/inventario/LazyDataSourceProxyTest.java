package com.example.microservicios.inventario;

import com.example.microservicios.persistence.routing.RoutingDataSource;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LazyDataSourceProxyTest {

    @Test
    @DisplayName("LazyConnectionDataSourceProxy defers physical connection acquisition until first statement")
    void shouldDeferPhysicalConnectionAcquisitionUntilFirstStatement() throws Exception {
        DataSource primary = mock(DataSource.class);
        DataSource replica1 = mock(DataSource.class);
        DataSource replica2 = mock(DataSource.class);

        Connection physicalConn = mock(Connection.class);
        Statement physicalStmt = mock(Statement.class);
        when(primary.getConnection()).thenReturn(physicalConn);
        when(physicalConn.createStatement()).thenReturn(physicalStmt);

        RoutingDataSource routingDataSource = new RoutingDataSource(primary, replica1, replica2);
        LazyConnectionDataSourceProxy lazyProxy = new LazyConnectionDataSourceProxy();
        lazyProxy.setTargetDataSource(routingDataSource);
        lazyProxy.setDefaultAutoCommit(true);
        lazyProxy.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        lazyProxy.afterPropertiesSet();

        // Step 1: Getting connection from lazy proxy returns proxy without touching physical DataSource
        Connection proxyConn = lazyProxy.getConnection();
        assertThat(proxyConn).isNotNull();
        verify(primary, never()).getConnection();
        verify(replica1, never()).getConnection();
        verify(replica2, never()).getConnection();

        // Step 2: First statement execution forces physical connection acquisition
        proxyConn.createStatement();
        verify(primary, times(1)).getConnection();

        // Step 3: Closing proxy connection closes physical connection
        proxyConn.close();
        verify(physicalConn, times(1)).close();
    }
}
