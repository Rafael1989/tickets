package com.ticketwave.partner.service;

import com.ticketwave.partner.dto.PartnerCredentialIssuedResponse;
import com.ticketwave.partner.dto.PartnerCredentialResponse;
import com.ticketwave.partner.dto.PartnerTokenResponse;

import java.util.List;

public interface PartnerApiCredentialService {

    /**
     * Admin-only. Generates a new client_id/client_secret pair for a
     * partner's machine-to-machine access. The raw secret is only ever
     * present in this call's response — see PartnerCredentialIssuedResponse.
     *
     * @throws com.ticketwave.partner.exception.PartnerNotFoundException if no such partner exists
     */
    PartnerCredentialIssuedResponse issueCredential(String actorUsername, Long partnerId);

    /**
     * Admin-only. Lists a partner's credentials, newest first — never
     * includes a secret.
     */
    List<PartnerCredentialResponse> listCredentials(Long partnerId);

    /**
     * Admin-only. Immediately stops the credential from minting new access
     * tokens. Tokens already issued under it keep working until their own
     * short TTL expires — this app has no distributed token blocklist to
     * revoke them individually, matching its actual infrastructure (no
     * shared cache) rather than pretending to a stronger guarantee.
     *
     * @throws com.ticketwave.partner.exception.PartnerCredentialNotFoundException if no such credential exists
     */
    void revokeCredential(String actorUsername, Long credentialId);

    /**
     * Public (unauthenticated) OAuth2 client-credentials grant: exchanges a
     * clientId/clientSecret pair for a short-lived Bearer token carrying the
     * PARTNER_API role. Fails the same generic way (see
     * InvalidPartnerCredentialsException) whether the clientId is unknown,
     * the secret is wrong, the credential is revoked, or the owning partner
     * isn't ACTIVE.
     */
    PartnerTokenResponse issueToken(String clientId, String clientSecret);
}
