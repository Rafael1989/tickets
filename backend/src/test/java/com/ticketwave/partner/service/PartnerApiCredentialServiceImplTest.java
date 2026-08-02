package com.ticketwave.partner.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.auth.JwtService;
import com.ticketwave.partner.dto.PartnerCredentialIssuedResponse;
import com.ticketwave.partner.dto.PartnerCredentialResponse;
import com.ticketwave.partner.dto.PartnerTokenResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerApiCredential;
import com.ticketwave.partner.entity.PartnerCredentialStatus;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.exception.InvalidPartnerCredentialsException;
import com.ticketwave.partner.exception.PartnerCredentialNotFoundException;
import com.ticketwave.partner.exception.PartnerNotFoundException;
import com.ticketwave.partner.mapper.PartnerCredentialMapper;
import com.ticketwave.partner.repository.PartnerApiCredentialRepository;
import com.ticketwave.partner.repository.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartnerApiCredentialServiceImplTest {

    @Mock
    private PartnerApiCredentialRepository credentialRepository;
    @Mock
    private PartnerRepository partnerRepository;
    @Mock
    private PartnerCredentialMapper credentialMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PartnerApiCredentialServiceImpl credentialService;

    private static Partner activePartner() {
        return Partner.builder().id(9L).name("Acme Transit").status(PartnerStatus.ACTIVE).build();
    }

    @Test
    void issueCredential_whenPartnerFound_generatesAndPersistsHashedSecret() {
        Partner partner = Partner.builder().id(9L).name("Acme Transit").build();
        given(partnerRepository.findById(9L)).willReturn(Optional.of(partner));
        given(passwordEncoder.encode(any())).willReturn("hashed-secret");
        given(credentialRepository.save(any())).willAnswer(inv -> {
            PartnerApiCredential c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        PartnerCredentialIssuedResponse response = credentialService.issueCredential("admin1", 9L);

        assertThat(response.partnerId()).isEqualTo(9L);
        assertThat(response.clientId()).startsWith("pk_");
        assertThat(response.clientSecret()).isNotBlank();

        ArgumentCaptor<PartnerApiCredential> captor = ArgumentCaptor.forClass(PartnerApiCredential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getClientSecretHash()).isEqualTo("hashed-secret");
        assertThat(captor.getValue().getStatus()).isEqualTo(PartnerCredentialStatus.ACTIVE);
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void issueCredential_whenPartnerMissing_throwsPartnerNotFoundException() {
        given(partnerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.issueCredential("admin1", 99L))
                .isInstanceOf(PartnerNotFoundException.class);
    }

    @Test
    void listCredentials_returnsEveryCredentialMappedToResponse() {
        PartnerApiCredential credential = PartnerApiCredential.builder().id(1L).partner(activePartner()).clientId("pk_abc").build();
        given(credentialRepository.findByPartnerId(9L)).willReturn(List.of(credential));
        PartnerCredentialResponse response = new PartnerCredentialResponse(1L, 9L, "pk_abc", PartnerCredentialStatus.ACTIVE, Instant.now(), null, null);
        given(credentialMapper.toResponse(credential)).willReturn(response);

        assertThat(credentialService.listCredentials(9L)).containsExactly(response);
    }

    @Test
    void revokeCredential_whenFound_setsStatusRevokedAndAudits() {
        PartnerApiCredential credential = PartnerApiCredential.builder().id(1L).status(PartnerCredentialStatus.ACTIVE).build();
        given(credentialRepository.findById(1L)).willReturn(Optional.of(credential));

        credentialService.revokeCredential("admin1", 1L);

        assertThat(credential.getStatus()).isEqualTo(PartnerCredentialStatus.REVOKED);
        assertThat(credential.getRevokedAt()).isNotNull();
        verify(auditService).record("admin1", "PARTNER_CREDENTIAL_REVOKED", "PARTNER_API_CREDENTIAL", 1L, null);
    }

    @Test
    void revokeCredential_whenMissing_throwsPartnerCredentialNotFoundException() {
        given(credentialRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.revokeCredential("admin1", 99L))
                .isInstanceOf(PartnerCredentialNotFoundException.class);
    }

    @Test
    void issueToken_withValidActiveCredentialAndPartner_returnsBearerToken() {
        PartnerApiCredential credential = PartnerApiCredential.builder().clientId("pk_abc").clientSecretHash("hash")
                .status(PartnerCredentialStatus.ACTIVE).partner(activePartner()).build();
        given(credentialRepository.findByClientId("pk_abc")).willReturn(Optional.of(credential));
        given(passwordEncoder.matches("secret", "hash")).willReturn(true);
        given(jwtService.generateAccessToken("pk_abc", List.of("PARTNER_API"))).willReturn("jwt-token");
        given(jwtService.getAccessTokenTtlSeconds()).willReturn(900L);

        PartnerTokenResponse response = credentialService.issueToken("pk_abc", "secret");

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(credential.getLastUsedAt()).isNotNull();
    }

    @Test
    void issueToken_withUnknownClientId_throwsInvalidPartnerCredentialsException() {
        given(credentialRepository.findByClientId("pk_ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.issueToken("pk_ghost", "secret"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void issueToken_withWrongSecret_throwsInvalidPartnerCredentialsException() {
        PartnerApiCredential credential = PartnerApiCredential.builder().clientId("pk_abc").clientSecretHash("hash")
                .status(PartnerCredentialStatus.ACTIVE).partner(activePartner()).build();
        given(credentialRepository.findByClientId("pk_abc")).willReturn(Optional.of(credential));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        assertThatThrownBy(() -> credentialService.issueToken("pk_abc", "wrong"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void issueToken_withRevokedCredential_throwsInvalidPartnerCredentialsExceptionWithoutCheckingSecret() {
        PartnerApiCredential credential = PartnerApiCredential.builder().clientId("pk_abc").clientSecretHash("hash")
                .status(PartnerCredentialStatus.REVOKED).partner(activePartner()).build();
        given(credentialRepository.findByClientId("pk_abc")).willReturn(Optional.of(credential));

        assertThatThrownBy(() -> credentialService.issueToken("pk_abc", "secret"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void issueToken_withSuspendedPartner_throwsInvalidPartnerCredentialsException() {
        Partner suspended = Partner.builder().id(9L).status(PartnerStatus.SUSPENDED).build();
        PartnerApiCredential credential = PartnerApiCredential.builder().clientId("pk_abc").clientSecretHash("hash")
                .status(PartnerCredentialStatus.ACTIVE).partner(suspended).build();
        given(credentialRepository.findByClientId("pk_abc")).willReturn(Optional.of(credential));

        assertThatThrownBy(() -> credentialService.issueToken("pk_abc", "secret"))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }
}
