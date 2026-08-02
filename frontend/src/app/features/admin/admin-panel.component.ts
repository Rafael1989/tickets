import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuditLogResponse } from '../../core/models/audit.model';
import { UserRole, UserResponse } from '../../core/models/admin-user.model';
import {
  PartnerCredentialResponse,
  PartnerResponse,
  PartnerStatus,
  PartnerWebhookResponse,
  WebhookStatus,
} from '../../core/models/partner.model';
import { DiscountType, PromoCodeResponse } from '../../core/models/promo.model';
import { AdminUserService } from '../../core/services/admin-user.service';
import { AuditService } from '../../core/services/audit.service';
import { NotificationService } from '../../core/services/notification.service';
import { PartnerService } from '../../core/services/partner.service';
import { PromoService } from '../../core/services/promo.service';

type AdminTab = 'users' | 'partners' | 'audit' | 'promos';

interface PendingRoleChange {
  user: UserResponse;
  role: UserRole;
}

interface PendingPartnerStatusChange {
  partner: PartnerResponse;
  status: PartnerStatus;
}

/**
 * Admin console for US8 (users/audit) and the multi-tenant partner
 * management added on top of it: onboarding, activation, OAuth2
 * client-credential issuance, and webhook registration. Role/status changes
 * require an explicit confirm step since they're security-sensitive; a
 * newly issued credential/webhook secret is shown exactly once (matching
 * the backend, which never returns it again) and must be copied before
 * navigating away.
 */
@Component({
  selector: 'tw-admin-panel',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './admin-panel.component.html',
  styleUrl: './admin-panel.component.scss',
})
export class AdminPanelComponent {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(AdminUserService);
  private readonly auditService = inject(AuditService);
  private readonly partnerService = inject(PartnerService);
  private readonly promoService = inject(PromoService);
  private readonly notifications = inject(NotificationService);

  readonly tab = signal<AdminTab>('users');
  readonly users = signal<UserResponse[]>([]);
  readonly loadingUsers = signal(true);
  readonly creatingUser = signal(false);
  readonly pendingRoleChange = signal<PendingRoleChange | null>(null);
  readonly updatingRoleForUserId = signal<number | null>(null);

  readonly userFilterForm = this.fb.nonNullable.group({
    query: [''],
    role: this.fb.nonNullable.control<UserRole | ''>(''),
  });
  private readonly userFilterValue = toSignal(this.userFilterForm.valueChanges, {
    initialValue: this.userFilterForm.getRawValue(),
  });

  readonly filteredUsers = computed(() => {
    const { query, role } = this.userFilterValue();
    const q = (query ?? '').trim().toLowerCase();
    return this.users().filter((user) => {
      const matchesRole = !role || user.role === role;
      const matchesQuery = !q || user.username.toLowerCase().includes(q) || user.email.toLowerCase().includes(q);
      return matchesRole && matchesQuery;
    });
  });

  readonly auditLog = signal<AuditLogResponse[]>([]);
  readonly loadingAudit = signal(false);
  readonly auditLoaded = signal(false);

  readonly auditFilterForm = this.fb.nonNullable.group({
    actor: [''],
    action: [''],
    entityType: [''],
    from: [''],
    to: [''],
  });

  readonly newUserForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: this.fb.nonNullable.control<UserRole>('OPERATOR'),
    partnerId: this.fb.nonNullable.control<number | null>(null),
  });

  // --- Partners ---
  readonly partners = signal<PartnerResponse[]>([]);
  readonly loadingPartners = signal(false);
  readonly partnersLoaded = signal(false);
  readonly creatingPartner = signal(false);
  readonly pendingPartnerStatusChange = signal<PendingPartnerStatusChange | null>(null);
  readonly updatingPartnerStatusId = signal<number | null>(null);
  readonly selectedPartnerId = signal<number | null>(null);
  readonly selectedPartner = computed(() => this.partners().find((p) => p.id === this.selectedPartnerId()) ?? null);

  readonly newPartnerForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    contactEmail: ['', [Validators.required, Validators.email]],
    commissionRate: this.fb.nonNullable.control(0.1, [Validators.required, Validators.min(0), Validators.max(1)]),
  });

  readonly credentials = signal<PartnerCredentialResponse[]>([]);
  readonly loadingCredentials = signal(false);
  readonly issuingCredential = signal(false);
  readonly revokingCredentialId = signal<number | null>(null);
  readonly justIssuedSecret = signal<{ clientId: string; clientSecret: string } | null>(null);

  readonly webhooks = signal<PartnerWebhookResponse[]>([]);
  readonly loadingWebhooks = signal(false);
  readonly registeringWebhook = signal(false);
  readonly updatingWebhookStatusId = signal<number | null>(null);
  readonly justRegisteredWebhookSecret = signal<{ url: string; secret: string } | null>(null);

  readonly newWebhookForm = this.fb.nonNullable.group({
    url: ['', Validators.required],
    eventType: this.fb.nonNullable.control('BOOKING_CANCELLED', Validators.required),
  });

  // --- Promo codes ---
  readonly promoCodes = signal<PromoCodeResponse[]>([]);
  readonly loadingPromoCodes = signal(false);
  readonly promoCodesLoaded = signal(false);
  readonly creatingPromoCode = signal(false);
  readonly updatingPromoStatusId = signal<number | null>(null);

  readonly newPromoForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(30)]],
    discountType: this.fb.nonNullable.control<DiscountType>('PERCENTAGE', Validators.required),
    discountValue: this.fb.nonNullable.control(10, [Validators.required, Validators.min(0.01)]),
    validFrom: ['', Validators.required],
    validTo: ['', Validators.required],
    maxRedemptions: this.fb.nonNullable.control<number | null>(null),
  });

  constructor() {
    this.refreshUsers();
  }

  selectTab(tab: AdminTab): void {
    this.tab.set(tab);
    if (tab === 'audit' && !this.auditLoaded()) {
      this.searchAudit();
    }
    if (tab === 'partners' && !this.partnersLoaded()) {
      this.refreshPartners();
    }
    if (tab === 'promos' && !this.promoCodesLoaded()) {
      this.refreshPromoCodes();
    }
  }

  private refreshUsers(): void {
    this.loadingUsers.set(true);
    this.userService
      .listUsers()
      .pipe(finalize(() => this.loadingUsers.set(false)))
      .subscribe({ next: (users) => this.users.set(users) });
  }

  searchAudit(): void {
    this.loadingAudit.set(true);
    this.auditLoaded.set(true);
    const { actor, action, entityType, from, to } = this.auditFilterForm.getRawValue();
    this.auditService
      .listAudit({
        actor: actor.trim() || undefined,
        action: action.trim() || undefined,
        entityType: entityType.trim() || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      })
      .pipe(finalize(() => this.loadingAudit.set(false)))
      .subscribe({ next: (entries) => this.auditLog.set(entries) });
  }

  resetAuditFilters(): void {
    this.auditFilterForm.reset({ actor: '', action: '', entityType: '', from: '', to: '' });
    this.searchAudit();
  }

  createUser(): void {
    if (this.newUserForm.invalid || this.creatingUser()) {
      return;
    }

    const { username, email, password, role, partnerId } = this.newUserForm.getRawValue();
    this.creatingUser.set(true);
    this.userService
      .createUser({ username, email, password, role, partnerId: role === 'OPERATOR' ? partnerId : null })
      .pipe(finalize(() => this.creatingUser.set(false)))
      .subscribe({
        next: (user) => {
          this.users.update((current) => [...current, user]);
          this.notifications.success(`Account '${user.username}' created as ${user.role}.`);
          this.newUserForm.reset({ username: '', email: '', password: '', role: 'OPERATOR', partnerId: null });
        },
      });
  }

  requestRoleChange(user: UserResponse, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as UserRole;
    if (role === user.role) {
      return;
    }
    this.pendingRoleChange.set({ user, role });
  }

  cancelRoleChange(): void {
    this.pendingRoleChange.set(null);
  }

  confirmRoleChange(): void {
    const pending = this.pendingRoleChange();
    if (!pending || this.updatingRoleForUserId()) {
      return;
    }

    this.updatingRoleForUserId.set(pending.user.id);
    this.userService
      .updateRole(pending.user.id, { role: pending.role })
      .pipe(finalize(() => this.updatingRoleForUserId.set(null)))
      .subscribe({
        next: (updated) => {
          this.users.update((current) => current.map((u) => (u.id === updated.id ? updated : u)));
          this.notifications.success(`${updated.username} is now ${updated.role}.`);
          this.pendingRoleChange.set(null);
        },
        error: () => this.pendingRoleChange.set(null),
      });
  }

  // --- Partners ---

  private refreshPartners(): void {
    this.loadingPartners.set(true);
    this.partnersLoaded.set(true);
    this.partnerService
      .listPartners()
      .pipe(finalize(() => this.loadingPartners.set(false)))
      .subscribe({ next: (partners) => this.partners.set(partners) });
  }

  createPartner(): void {
    if (this.newPartnerForm.invalid || this.creatingPartner()) {
      return;
    }

    this.creatingPartner.set(true);
    this.partnerService
      .createPartner(this.newPartnerForm.getRawValue())
      .pipe(finalize(() => this.creatingPartner.set(false)))
      .subscribe({
        next: (partner) => {
          this.partners.update((current) => [...current, partner]);
          this.notifications.success(`Partner '${partner.name}' created (PENDING).`);
          this.newPartnerForm.reset({ name: '', contactEmail: '', commissionRate: 0.1 });
        },
      });
  }

  requestPartnerStatusChange(partner: PartnerResponse, event: Event): void {
    const status = (event.target as HTMLSelectElement).value as PartnerStatus;
    if (status === partner.status) {
      return;
    }
    this.pendingPartnerStatusChange.set({ partner, status });
  }

  cancelPartnerStatusChange(): void {
    this.pendingPartnerStatusChange.set(null);
  }

  confirmPartnerStatusChange(): void {
    const pending = this.pendingPartnerStatusChange();
    if (!pending || this.updatingPartnerStatusId()) {
      return;
    }

    this.updatingPartnerStatusId.set(pending.partner.id);
    this.partnerService
      .updateStatus(pending.partner.id, pending.status)
      .pipe(finalize(() => this.updatingPartnerStatusId.set(null)))
      .subscribe({
        next: (updated) => {
          this.partners.update((current) => current.map((p) => (p.id === updated.id ? updated : p)));
          this.notifications.success(`${updated.name} is now ${updated.status}.`);
          this.pendingPartnerStatusChange.set(null);
        },
        error: () => this.pendingPartnerStatusChange.set(null),
      });
  }

  selectPartner(partner: PartnerResponse): void {
    this.selectedPartnerId.set(partner.id);
    this.justIssuedSecret.set(null);
    this.justRegisteredWebhookSecret.set(null);
    this.refreshCredentials(partner.id);
    this.refreshWebhooks(partner.id);
  }

  closePartnerDetail(): void {
    this.selectedPartnerId.set(null);
  }

  private refreshCredentials(partnerId: number): void {
    this.loadingCredentials.set(true);
    this.partnerService
      .listCredentials(partnerId)
      .pipe(finalize(() => this.loadingCredentials.set(false)))
      .subscribe({ next: (credentials) => this.credentials.set(credentials) });
  }

  issueCredential(): void {
    const partner = this.selectedPartner();
    if (!partner || this.issuingCredential()) {
      return;
    }

    this.issuingCredential.set(true);
    this.partnerService
      .issueCredential(partner.id)
      .pipe(finalize(() => this.issuingCredential.set(false)))
      .subscribe({
        next: (issued) => {
          this.justIssuedSecret.set({ clientId: issued.clientId, clientSecret: issued.clientSecret });
          this.refreshCredentials(partner.id);
          this.notifications.success(`Credential ${issued.clientId} issued.`);
        },
      });
  }

  dismissIssuedSecret(): void {
    this.justIssuedSecret.set(null);
  }

  revokeCredential(credential: PartnerCredentialResponse): void {
    if (this.revokingCredentialId()) {
      return;
    }
    const partner = this.selectedPartner();
    if (!partner) {
      return;
    }

    this.revokingCredentialId.set(credential.id);
    this.partnerService
      .revokeCredential(credential.id)
      .pipe(finalize(() => this.revokingCredentialId.set(null)))
      .subscribe({
        next: () => {
          this.notifications.success(`Credential ${credential.clientId} revoked.`);
          this.refreshCredentials(partner.id);
        },
      });
  }

  private refreshWebhooks(partnerId: number): void {
    this.loadingWebhooks.set(true);
    this.partnerService
      .listWebhooks(partnerId)
      .pipe(finalize(() => this.loadingWebhooks.set(false)))
      .subscribe({ next: (webhooks) => this.webhooks.set(webhooks) });
  }

  registerWebhook(): void {
    const partner = this.selectedPartner();
    if (!partner || this.newWebhookForm.invalid || this.registeringWebhook()) {
      return;
    }

    this.registeringWebhook.set(true);
    this.partnerService
      .registerWebhook(partner.id, this.newWebhookForm.getRawValue())
      .pipe(finalize(() => this.registeringWebhook.set(false)))
      .subscribe({
        next: (issued) => {
          this.justRegisteredWebhookSecret.set({ url: issued.url, secret: issued.secret });
          this.refreshWebhooks(partner.id);
          this.newWebhookForm.reset({ url: '', eventType: 'BOOKING_CANCELLED' });
          this.notifications.success(`Webhook registered for ${issued.url}.`);
        },
      });
  }

  dismissWebhookSecret(): void {
    this.justRegisteredWebhookSecret.set(null);
  }

  toggleWebhookStatus(webhook: PartnerWebhookResponse): void {
    if (this.updatingWebhookStatusId()) {
      return;
    }
    const partner = this.selectedPartner();
    if (!partner) {
      return;
    }
    const nextStatus: WebhookStatus = webhook.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';

    this.updatingWebhookStatusId.set(webhook.id);
    this.partnerService
      .updateWebhookStatus(webhook.id, nextStatus)
      .pipe(finalize(() => this.updatingWebhookStatusId.set(null)))
      .subscribe({
        next: (updated) => {
          this.webhooks.update((current) => current.map((w) => (w.id === updated.id ? updated : w)));
          this.notifications.success(`Webhook for ${updated.url} is now ${updated.status}.`);
        },
      });
  }

  // --- Promo codes ---

  private refreshPromoCodes(): void {
    this.loadingPromoCodes.set(true);
    this.promoCodesLoaded.set(true);
    this.promoService
      .listPromoCodes()
      .pipe(finalize(() => this.loadingPromoCodes.set(false)))
      .subscribe({ next: (codes) => this.promoCodes.set(codes) });
  }

  createPromoCode(): void {
    if (this.newPromoForm.invalid || this.creatingPromoCode()) {
      return;
    }

    const { code, discountType, discountValue, validFrom, validTo, maxRedemptions } = this.newPromoForm.getRawValue();
    this.creatingPromoCode.set(true);
    this.promoService
      .createPromoCode({
        code: code.trim().toUpperCase(),
        discountType,
        discountValue,
        validFrom: new Date(validFrom).toISOString(),
        validTo: new Date(validTo).toISOString(),
        maxRedemptions: maxRedemptions || null,
      })
      .pipe(finalize(() => this.creatingPromoCode.set(false)))
      .subscribe({
        next: (created) => {
          this.promoCodes.update((current) => [created, ...current]);
          this.notifications.success(`Promo code '${created.code}' created.`);
          this.newPromoForm.reset({
            code: '',
            discountType: 'PERCENTAGE',
            discountValue: 10,
            validFrom: '',
            validTo: '',
            maxRedemptions: null,
          });
        },
      });
  }

  togglePromoStatus(promo: PromoCodeResponse): void {
    if (this.updatingPromoStatusId()) {
      return;
    }

    this.updatingPromoStatusId.set(promo.id);
    this.promoService
      .updateStatus(promo.id, !promo.active)
      .pipe(finalize(() => this.updatingPromoStatusId.set(null)))
      .subscribe({
        next: (updated) => {
          this.promoCodes.update((current) => current.map((p) => (p.id === updated.id ? updated : p)));
          this.notifications.success(`Promo code '${updated.code}' is now ${updated.active ? 'active' : 'inactive'}.`);
        },
      });
  }
}
