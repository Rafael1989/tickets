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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class PartnerApiCredentialServiceImpl implements PartnerApiCredentialService {

    private static final String PARTNER_API_ROLE = "PARTNER_API";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PartnerApiCredentialRepository credentialRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerCredentialMapper credentialMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public PartnerApiCredentialServiceImpl(
            PartnerApiCredentialRepository credentialRepository,
            PartnerRepository partnerRepository,
            PartnerCredentialMapper credentialMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.credentialRepository = credentialRepository;
        this.partnerRepository = partnerRepository;
        this.credentialMapper = credentialMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PartnerCredentialIssuedResponse issueCredential(String actorUsername, Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));

        String clientId = "pk_" + randomToken(16);
        String clientSecret = randomToken(32);

        PartnerApiCredential credential = credentialRepository.save(PartnerApiCredential.builder()
                .partner(partner)
                .clientId(clientId)
                .clientSecretHash(passwordEncoder.encode(clientSecret))
                .status(PartnerCredentialStatus.ACTIVE)
                .build());

        auditService.record(actorUsername, "PARTNER_CREDENTIAL_ISSUED", "PARTNER_API_CREDENTIAL", credential.getId(),
                "partnerId=" + partnerId + " clientId=" + clientId);
        return new PartnerCredentialIssuedResponse(credential.getId(), partnerId, clientId, clientSecret, credential.getCreatedAt());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PartnerCredentialResponse> listCredentials(Long partnerId) {
        return credentialRepository.findByPartnerId(partnerId).stream()
                .map(credentialMapper::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void revokeCredential(String actorUsername, Long credentialId) {
        PartnerApiCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new PartnerCredentialNotFoundException(credentialId));

        credential.setStatus(PartnerCredentialStatus.REVOKED);
        credential.setRevokedAt(Instant.now());

        auditService.record(actorUsername, "PARTNER_CREDENTIAL_REVOKED", "PARTNER_API_CREDENTIAL", credentialId, null);
    }

    @Override
    @Transactional
    public PartnerTokenResponse issueToken(String clientId, String clientSecret) {
        PartnerApiCredential credential = credentialRepository.findByClientId(clientId)
                .orElseThrow(InvalidPartnerCredentialsException::new);

        if (credential.getStatus() != PartnerCredentialStatus.ACTIVE
                || credential.getPartner().getStatus() != PartnerStatus.ACTIVE
                || !passwordEncoder.matches(clientSecret, credential.getClientSecretHash())) {
            throw new InvalidPartnerCredentialsException();
        }

        credential.setLastUsedAt(Instant.now());

        String accessToken = jwtService.generateAccessToken(clientId, List.of(PARTNER_API_ROLE));
        return new PartnerTokenResponse(accessToken, "Bearer", jwtService.getAccessTokenTtlSeconds());
    }

    private static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
