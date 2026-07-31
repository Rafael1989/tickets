package com.ticketwave.audit.mapper;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
