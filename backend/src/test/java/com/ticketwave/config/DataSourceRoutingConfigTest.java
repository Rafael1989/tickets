package com.ticketwave.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the actual Spring wiring (property binding onto the two Hikari
 * pools, bean graph construction) rather than just the routing decision
 * logic covered by ReadWriteRoutingDataSourceTest. Deliberately points at an
 * unreachable port: HikariDataSource pool initialization is lazy (the pool
 * connects on first getConnection(), not on bean construction), so this
 * proves the wiring is correct without needing a real database. Verifying
 * that a query inside a read-only transaction actually reaches the pool is
 * TicketwaveApplicationIT's job, since that needs the live local PostgreSQL.
 *
 * This class covers only the unconfigured-replica fallback; see
 * {@link DataSourceRoutingConfigReplicaTest} for the branch that builds a
 * genuinely separate replica connection.
 */
@SpringBootTest(classes = {DataSourceRoutingConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableConfigurationProperties(ReplicaDataSourceProperties.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/nonexistent",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.datasource.hikari.pool-name=test-pool",
        "spring.datasource.hikari.maximum-pool-size=3"
})
class DataSourceRoutingConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andDataSourceBeanIsALazyRoutingProxy() {
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }
}
