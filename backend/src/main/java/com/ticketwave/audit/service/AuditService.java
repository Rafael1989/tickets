package com.ticketwave.audit.service;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.entity.AuditLog;
import com.ticketwave.audit.mapper.AuditLogMapper;
import com.ticketwave.audit.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records a lightweight, admin-visible trail of significant actions
 * (account creation, refund decisions). record() itself is called from
 * within other services as a side effect of ordinary user/support actions,
 * so it's deliberately not access-restricted — only reading the trail
 * (listAll) is admin-only. No interface: this is trivial CRUD with no
 * business logic worth mocking independently of its repository.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional
    public void record(String actorUsername, String action, String entityType, Long entityId, String details) {
        auditLogRepository.save(AuditLog.builder()
                .actorUsername(actorUsername)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(auditLogMapper::toResponse)
                .toList();
    }
}
