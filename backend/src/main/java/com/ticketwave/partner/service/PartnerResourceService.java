package com.ticketwave.partner.service;

import com.ticketwave.catalog.dto.RouteResponse;

import java.util.List;

/**
 * Resource endpoints callable with a PARTNER_API access token (see
 * PartnerApiCredentialService.issueToken) — the machine-to-machine
 * counterpart to the OPERATOR-role, human-login endpoints in
 * catalog.controller.RouteController.
 */
public interface PartnerResourceService {

    /**
     * Every route owned by any operator under the calling credential's
     * partner. Re-validates the credential (active, not revoked) and its
     * partner (ACTIVE) on every call, not just at token issuance — so
     * revoking a credential blocks it here immediately, without waiting for
     * its access token to expire.
     *
     * @throws com.ticketwave.partner.exception.InvalidPartnerCredentialsException if the credential is unknown, revoked, or its partner isn't ACTIVE
     */
    List<RouteResponse> listRoutes(String clientId);
}
