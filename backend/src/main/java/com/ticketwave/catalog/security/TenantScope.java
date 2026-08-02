package com.ticketwave.catalog.security;

import com.ticketwave.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Ownership check for operator-managed resources (routes, vehicles, drivers,
 * schedules): true for the resource's own creator, or for any other OPERATOR
 * sharing the same non-null partner — so a partner company's staff can
 * collectively manage its inventory instead of each login being its own
 * silo. A standalone operator with no partner (partner == null) keeps the
 * pre-multi-tenant behavior of only ever matching itself.
 */
@Component
public class TenantScope {

    public boolean isSameTenant(User resourceOwner, User caller) {
        if (resourceOwner.getId().equals(caller.getId())) {
            return true;
        }
        return resourceOwner.getPartner() != null
                && caller.getPartner() != null
                && resourceOwner.getPartner().getId().equals(caller.getPartner().getId());
    }
}
