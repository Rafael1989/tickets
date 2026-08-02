package com.ticketwave;

import com.ticketwave.config.InventoryProperties;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.PricingProperties;
import com.ticketwave.config.RateLimitProperties;
import com.ticketwave.config.RefundProperties;
import com.ticketwave.config.ReplicaDataSourceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class, InventoryProperties.class, PricingProperties.class, RefundProperties.class,
        RateLimitProperties.class, ReplicaDataSourceProperties.class
})
@EnableScheduling
@EnableAsync
public class TicketwaveApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketwaveApplication.class, args);
    }
}
