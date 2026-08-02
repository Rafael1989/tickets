package com.ticketwave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All fields optional and blank by default — see DataSourceRoutingConfig for
 * how an unconfigured replica falls back to the primary connection, making
 * this a genuine no-op until a real read replica exists to point at.
 */
@ConfigurationProperties(prefix = "ticketwave.datasource.replica")
public record ReplicaDataSourceProperties(
        String url,
        String username,
        String password
) {
}
