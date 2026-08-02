package com.ticketwave.partner.service;

import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.entity.PartnerStatus;

import java.util.List;

public interface PartnerService {

    /**
     * Admin-only. Onboards a new partner in PENDING status — activating it
     * (making its operators' inventory bookable) is a separate step via
     * {@link #updateStatus}, so a partial/incorrect onboarding never goes
     * live by accident.
     *
     * @throws com.ticketwave.partner.exception.DuplicatePartnerException if the name is already taken
     */
    PartnerResponse createPartner(String actorUsername, PartnerRequest request);

    /**
     * Admin-only.
     *
     * @throws com.ticketwave.partner.exception.PartnerNotFoundException if no such partner exists
     */
    PartnerResponse getPartner(Long partnerId);

    /**
     * Admin-only. Lists every partner.
     */
    List<PartnerResponse> listPartners();

    /**
     * Admin-only. Moves a partner between PENDING/ACTIVE/SUSPENDED.
     * Suspending a partner doesn't itself touch its operators' existing
     * routes/schedules — see the docs on the OAuth2 token endpoint and
     * webhook delivery for what suspension actually blocks.
     *
     * @throws com.ticketwave.partner.exception.PartnerNotFoundException if no such partner exists
     */
    PartnerResponse updateStatus(String actorUsername, Long partnerId, PartnerStatus status);
}
