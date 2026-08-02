package com.ticketwave.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadWriteRoutingDataSourceTest {

    private final DataSource primary = mock(DataSource.class);
    private final DataSource replica = mock(DataSource.class);

    private ReadWriteRoutingDataSource routingDataSource() {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of(
                ReadWriteRoutingDataSource.Route.PRIMARY, primary,
                ReadWriteRoutingDataSource.Route.REPLICA, replica));
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return routing;
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void getConnection_insideReadOnlyTransaction_routesToReplica() throws SQLException {
        Connection replicaConnection = mock(Connection.class);
        when(replica.getConnection()).thenReturn(replicaConnection);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Connection result = routingDataSource().getConnection();

        assertThat(result).isSameAs(replicaConnection);
    }

    @Test
    void getConnection_insideWriteTransaction_routesToPrimary() throws SQLException {
        Connection primaryConnection = mock(Connection.class);
        when(primary.getConnection()).thenReturn(primaryConnection);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Connection result = routingDataSource().getConnection();

        assertThat(result).isSameAs(primaryConnection);
    }

    @Test
    void getConnection_withNoActiveTransaction_routesToPrimary() throws SQLException {
        Connection primaryConnection = mock(Connection.class);
        when(primary.getConnection()).thenReturn(primaryConnection);

        Connection result = routingDataSource().getConnection();

        assertThat(result).isSameAs(primaryConnection);
    }
}
