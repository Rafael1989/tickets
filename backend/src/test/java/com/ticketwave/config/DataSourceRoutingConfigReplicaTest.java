package com.ticketwave.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configured-replica half of {@link DataSourceRoutingConfigTest}, which
 * covers only the fallback (no replica URL → REPLICA reuses the primary
 * connection). Split into its own class because the distinction is entirely a
 * property-binding one, and @TestPropertySource is per-context.
 *
 * Worth testing rather than assuming: the fallback path is what every other
 * test and every current environment exercises, so a bug in the branch that
 * actually builds a separate replica connection would stay invisible until
 * the first production deployment that sets the property — at which point
 * read traffic silently keeps hitting the primary, or worse, writes get
 * routed somewhere unintended.
 *
 * Like its sibling, this points at unreachable ports on purpose: Hikari pools
 * connect lazily on first getConnection(), so bean construction alone proves
 * the wiring without needing a live database.
 */
@SpringBootTest(classes = {DataSourceRoutingConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableConfigurationProperties(ReplicaDataSourceProperties.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/primary-db",
        "spring.datasource.username=primary-user",
        "spring.datasource.password=primary-pass",
        "spring.datasource.hikari.pool-name=test-pool",
        "spring.datasource.hikari.maximum-pool-size=3",
        "ticketwave.datasource.replica.url=jdbc:postgresql://localhost:2/replica-db",
        "ticketwave.datasource.replica.username=replica-user",
        "ticketwave.datasource.replica.password=replica-pass"
})
class DataSourceRoutingConfigReplicaTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private HikariDataSource primaryDataSource;

    @Autowired
    @Qualifier("replicaDataSource")
    private HikariDataSource replicaDataSource;

    @Test
    void replicaDataSource_whenReplicaUrlIsConfigured_usesTheReplicaCredentialsNotThePrimarys() {
        assertThat(replicaDataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:2/replica-db");
        assertThat(replicaDataSource.getUsername()).isEqualTo("replica-user");
        assertThat(replicaDataSource.getPassword()).isEqualTo("replica-pass");
    }

    @Test
    void primaryDataSource_isUnaffectedByTheReplicaConfiguration() {
        assertThat(primaryDataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:1/primary-db");
        assertThat(primaryDataSource.getUsername()).isEqualTo("primary-user");
    }
}
