package com.ticketwave.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Read-replica readiness: every existing @Transactional(readOnly = true)
 * method in this codebase (ScheduleCatalogCache, ScheduleSearchServiceImpl,
 * most getters across the service layer) already declares itself read-only
 * — that declaration is what this class turns into an actual routing
 * decision, so pointing search reads at a replica needs zero further code
 * changes once one exists.
 *
 * Until ticketwave.datasource.replica.url is set, REPLICA silently falls
 * back to the exact same connection as PRIMARY (just a second, separate
 * Hikari pool) — this is a genuine no-op today, not a half-built feature:
 * nothing behaves differently until an operator actually points it at a
 * replica.
 *
 * Liquibase deliberately bypasses this class entirely — see
 * spring.liquibase.url/user/password in application.yml, which are pinned
 * to the same value as spring.datasource.* so schema migrations always run
 * against the primary, never a replica, and never through the lazy proxy
 * below.
 */
@Configuration
public class DataSourceRoutingConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource replicaDataSource(
            DataSourceProperties primaryDataSourceProperties,
            ReplicaDataSourceProperties replicaDataSourceProperties
    ) {
        DataSourceProperties effective = StringUtils.hasText(replicaDataSourceProperties.url())
                ? toDataSourceProperties(replicaDataSourceProperties)
                : primaryDataSourceProperties;
        return effective.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * @Primary is required, not decorative: primaryDataSource and
     * replicaDataSource are both HikariDataSource (itself a DataSource), so
     * without it there are 3 unqualified DataSource-typed beans in the
     * context. Spring Boot's JPA autoconfiguration only creates
     * entityManagerFactory when it can resolve a single candidate
     * (@ConditionalOnSingleCandidate(DataSource.class)); with 3 ambiguous
     * candidates and none marked primary, that condition silently fails and
     * the whole JPA layer never gets configured — the app fails to start
     * with every JPA-repository-backed bean reporting a missing
     * entityManagerFactory, which doesn't mention this class at all.
     */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource primaryDataSource, HikariDataSource replicaDataSource) {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        routingDataSource.setTargetDataSources(Map.of(
                ReadWriteRoutingDataSource.Route.PRIMARY, primaryDataSource,
                ReadWriteRoutingDataSource.Route.REPLICA, replicaDataSource));
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.afterPropertiesSet();

        // Lazy so the actual connection isn't acquired (and determineCurrentLookupKey
        // isn't evaluated) until after Spring's transaction interceptor has
        // already set the read-only flag for the current transaction.
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private static DataSourceProperties toDataSourceProperties(ReplicaDataSourceProperties replica) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(replica.url());
        properties.setUsername(replica.username());
        properties.setPassword(replica.password());
        return properties;
    }
}
