package com.ticketwave.audit.service;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.entity.AuditLog;
import com.ticketwave.audit.mapper.AuditLogMapper;
import com.ticketwave.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditService auditService;

    @Test
    void record_savesAnAuditLogWithTheGivenFields() {
        auditService.record("alice", "USER_REGISTERED", "USER", 1L, "role=CUSTOMER");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUsername()).isEqualTo("alice");
        assertThat(saved.getAction()).isEqualTo("USER_REGISTERED");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getDetails()).isEqualTo("role=CUSTOMER");
    }

    @Test
    void listAll_returnsEntriesMappedToResponsesInRepositoryOrder() {
        AuditLog first = AuditLog.builder().id(2L).actorUsername("support1").action("REFUND_APPROVED")
                .entityType("REFUND").entityId(5L).createdAt(Instant.now()).build();
        AuditLog second = AuditLog.builder().id(1L).actorUsername("alice").action("USER_REGISTERED")
                .entityType("USER").entityId(1L).createdAt(Instant.now().minusSeconds(60)).build();
        given(auditLogRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(first, second));

        AuditLogResponse firstResponse = new AuditLogResponse(2L, "support1", "REFUND_APPROVED", "REFUND", 5L, null, first.getCreatedAt());
        AuditLogResponse secondResponse = new AuditLogResponse(1L, "alice", "USER_REGISTERED", "USER", 1L, "role=CUSTOMER", second.getCreatedAt());
        given(auditLogMapper.toResponse(first)).willReturn(firstResponse);
        given(auditLogMapper.toResponse(second)).willReturn(secondResponse);

        List<AuditLogResponse> result = auditService.listAll();

        assertThat(result).containsExactly(firstResponse, secondResponse);
    }

    @Test
    void listAll_whenNoEntries_returnsEmptyList() {
        given(auditLogRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        assertThat(auditService.listAll()).isEmpty();
    }
}
