export type PartnerStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED';

export interface PartnerResponse {
  id: number;
  name: string;
  contactEmail: string;
  status: PartnerStatus;
  commissionRate: number;
  createdAt: string;
}

export interface PartnerRequest {
  name: string;
  contactEmail: string;
  commissionRate: number;
}

export interface PartnerStatusUpdateRequest {
  status: PartnerStatus;
}

export type PartnerCredentialStatus = 'ACTIVE' | 'REVOKED';

/** Never carries a secret — see PartnerCredentialIssuedResponse for the one-time exception at issuance. */
export interface PartnerCredentialResponse {
  id: number;
  partnerId: number;
  clientId: string;
  status: PartnerCredentialStatus;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

/** Returned only once, from the issue call — clientSecret can never be retrieved again after this. */
export interface PartnerCredentialIssuedResponse {
  id: number;
  partnerId: number;
  clientId: string;
  clientSecret: string;
  createdAt: string;
}

export type WebhookStatus = 'ACTIVE' | 'DISABLED';

/** Never carries a signing secret — see PartnerWebhookIssuedResponse for the one-time exception at registration. */
export interface PartnerWebhookResponse {
  id: number;
  partnerId: number;
  url: string;
  eventType: string;
  status: WebhookStatus;
  createdAt: string;
}

export interface PartnerWebhookRequest {
  url: string;
  eventType: string;
}

/** Returned only once, from the register call — secret can never be retrieved again after this. */
export interface PartnerWebhookIssuedResponse {
  id: number;
  partnerId: number;
  url: string;
  secret: string;
  eventType: string;
  createdAt: string;
}

export interface WebhookStatusUpdateRequest {
  status: WebhookStatus;
}
