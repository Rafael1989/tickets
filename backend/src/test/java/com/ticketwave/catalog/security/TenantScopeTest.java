package com.ticketwave.catalog.security;

import com.ticketwave.partner.entity.Partner;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantScopeTest {

    private final TenantScope tenantScope = new TenantScope();

    private static User user(Long id, Partner partner) {
        return User.builder().id(id).partner(partner).build();
    }

    private static Partner partner(Long id) {
        return Partner.builder().id(id).build();
    }

    @Test
    void isSameTenant_whenCallerIsTheResourceOwner_returnsTrue() {
        User owner = user(1L, null);
        User caller = user(1L, null);

        assertThat(tenantScope.isSameTenant(owner, caller)).isTrue();
    }

    @Test
    void isSameTenant_whenOwnerHasNoPartner_returnsFalse() {
        User owner = user(1L, null);
        User caller = user(2L, partner(10L));

        assertThat(tenantScope.isSameTenant(owner, caller)).isFalse();
    }

    @Test
    void isSameTenant_whenCallerHasNoPartner_returnsFalse() {
        User owner = user(1L, partner(10L));
        User caller = user(2L, null);

        assertThat(tenantScope.isSameTenant(owner, caller)).isFalse();
    }

    @Test
    void isSameTenant_whenBothBelongToTheSamePartner_returnsTrue() {
        User owner = user(1L, partner(10L));
        User caller = user(2L, partner(10L));

        assertThat(tenantScope.isSameTenant(owner, caller)).isTrue();
    }

    @Test
    void isSameTenant_whenPartnersDiffer_returnsFalse() {
        User owner = user(1L, partner(10L));
        User caller = user(2L, partner(11L));

        assertThat(tenantScope.isSameTenant(owner, caller)).isFalse();
    }
}
