export interface AuditLogResponse {
  id: number;
  actorUsername: string;
  action: string;
  entityType: string;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}
