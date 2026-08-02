export interface AuditLogResponse {
  id: number;
  actorUsername: string;
  action: string;
  entityType: string;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}

/** All optional; an all-empty object matches every entry. from/to are ISO-8601 instants. */
export interface AuditLogSearchParams {
  actor?: string;
  action?: string;
  entityType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
