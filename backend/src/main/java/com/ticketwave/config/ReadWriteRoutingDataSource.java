package com.ticketwave.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A separate top-level class (rather than an anonymous one inline in
 * DataSourceRoutingConfig) specifically so the routing decision itself is
 * unit-testable without booting a Spring context or a real database — see
 * ReadWriteRoutingDataSourceTest.
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    public enum Route {
        PRIMARY, REPLICA
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? Route.REPLICA : Route.PRIMARY;
    }
}
