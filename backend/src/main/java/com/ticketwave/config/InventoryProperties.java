package com.ticketwave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketwave.inventory")
public record InventoryProperties(
        long seatHoldTtlMinutes
) {
}
