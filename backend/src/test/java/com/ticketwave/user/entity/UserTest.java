package com.ticketwave.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void onCreate_whenCreatedAtUnset_defaultsToNow() {
        User user = User.builder().build();

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void onCreate_whenCreatedAtAlreadySet_leavesItUnchanged() {
        Instant explicit = Instant.parse("2020-01-01T00:00:00Z");
        User user = User.builder().createdAt(explicit).build();

        user.onCreate();

        assertThat(user.getCreatedAt()).isEqualTo(explicit);
    }
}
