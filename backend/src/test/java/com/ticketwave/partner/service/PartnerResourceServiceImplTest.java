package com.ticketwave.partner.service;

import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerApiCredential;
import com.ticketwave.partner.entity.PartnerCredentialStatus;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.exception.InvalidPartnerCredentialsException;
import com.ticketwave.partner.repository.PartnerApiCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PartnerResourceServiceImplTest {

    @Mock
    private PartnerApiCredentialRepository credentialRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private RouteMapper routeMapper;

    @InjectMocks
    private PartnerResourceServiceImpl partnerResourceService;

    private static PartnerApiCredential credential(PartnerCredentialStatus status, PartnerStatus partnerStatus) {
        Partner partner = Partner.builder().id(9L).status(partnerStatus).build();
        return PartnerApiCredential.builder().clientId("pk_abc").status(status).partner(partner).build();
    }

    @Test
    void listRoutes_withActiveCredentialAndPartner_returnsThePartnersRoutes() {
        given(credentialRepository.findByClientId("pk_abc"))
                .willReturn(Optional.of(credential(PartnerCredentialStatus.ACTIVE, PartnerStatus.ACTIVE)));
        Route route = Route.builder().id(1L).type(RouteType.BUS).build();
        given(routeRepository.findByOperatorPartnerId(9L)).willReturn(List.of(route));
        RouteResponse response = new RouteResponse(1L, 2L, RouteType.BUS, "NYC", "Boston", null, 240);
        given(routeMapper.toResponse(route)).willReturn(response);

        assertThat(partnerResourceService.listRoutes("pk_abc")).containsExactly(response);
    }

    @Test
    void listRoutes_withUnknownClientId_throwsInvalidPartnerCredentialsException() {
        given(credentialRepository.findByClientId("pk_ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> partnerResourceService.listRoutes("pk_ghost"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void listRoutes_withRevokedCredential_throwsInvalidPartnerCredentialsException() {
        given(credentialRepository.findByClientId("pk_abc"))
                .willReturn(Optional.of(credential(PartnerCredentialStatus.REVOKED, PartnerStatus.ACTIVE)));

        assertThatThrownBy(() -> partnerResourceService.listRoutes("pk_abc"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void listRoutes_withSuspendedPartner_throwsInvalidPartnerCredentialsException() {
        given(credentialRepository.findByClientId("pk_abc"))
                .willReturn(Optional.of(credential(PartnerCredentialStatus.ACTIVE, PartnerStatus.SUSPENDED)));

        assertThatThrownBy(() -> partnerResourceService.listRoutes("pk_abc"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }
}
