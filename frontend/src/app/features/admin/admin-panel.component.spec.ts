import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuditLogResponse } from '../../core/models/audit.model';
import { UserResponse } from '../../core/models/admin-user.model';
import {
  PartnerCredentialIssuedResponse,
  PartnerCredentialResponse,
  PartnerResponse,
  PartnerWebhookIssuedResponse,
  PartnerWebhookResponse,
} from '../../core/models/partner.model';
import { PromoCodeResponse } from '../../core/models/promo.model';
import { AdminUserService } from '../../core/services/admin-user.service';
import { AuditService } from '../../core/services/audit.service';
import { NotificationService } from '../../core/services/notification.service';
import { PartnerService } from '../../core/services/partner.service';
import { PromoService } from '../../core/services/promo.service';
import { AdminPanelComponent } from './admin-panel.component';

describe('AdminPanelComponent', () => {
  let fixture: ComponentFixture<AdminPanelComponent>;
  let component: AdminPanelComponent;
  let userService: AdminUserService;
  let auditService: AuditService;
  let partnerService: PartnerService;
  let promoService: PromoService;
  let notifications: NotificationService;

  function cardContaining(headingText: string): HTMLElement {
    const headings = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('h3'),
    ) as HTMLElement[];
    const heading = headings.find((h) => h.textContent?.includes(headingText));
    return heading!.closest('.card') as HTMLElement;
  }

  const existingUser: UserResponse = {
    id: 1,
    username: 'alice',
    email: 'alice@example.com',
    role: 'CUSTOMER',
    partnerId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const existingPartner: PartnerResponse = {
    id: 9,
    name: 'Acme Transit',
    contactEmail: 'ops@acme.example',
    status: 'PENDING',
    commissionRate: 0.1,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const existingPromoCode: PromoCodeResponse = {
    id: 5,
    code: 'SAVE20',
    discountType: 'PERCENTAGE',
    discountValue: 20,
    validFrom: '2026-01-01T00:00:00Z',
    validTo: '2026-12-31T00:00:00Z',
    maxRedemptions: 100,
    redemptionCount: 0,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [AdminPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    userService = TestBed.inject(AdminUserService);
    vi.spyOn(userService, 'listUsers').mockReturnValue(of([existingUser]));

    auditService = TestBed.inject(AuditService);
    partnerService = TestBed.inject(PartnerService);
    promoService = TestBed.inject(PromoService);
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(AdminPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => await createComponent());

  it('loads the user list on init, defaulting to the users tab', () => {
    expect(component.tab()).toBe('users');
    expect(component.loadingUsers()).toBe(false);
    expect(component.users()).toEqual([existingUser]);
  });

  it('filteredUsers matches the search query against username or email', () => {
    component.userFilterForm.patchValue({ query: 'ALICE' });
    expect(component.filteredUsers()).toEqual([existingUser]);

    component.userFilterForm.patchValue({ query: 'nobody' });
    expect(component.filteredUsers()).toEqual([]);
  });

  it('filteredUsers matches the selected role', () => {
    component.userFilterForm.patchValue({ role: 'ADMIN' });
    expect(component.filteredUsers()).toEqual([]);

    component.userFilterForm.patchValue({ role: 'CUSTOMER' });
    expect(component.filteredUsers()).toEqual([existingUser]);
  });

  it('selectTab("audit") lazily loads the audit log only the first time', () => {
    const entries: AuditLogResponse[] = [
      { id: 1, actorUsername: 'alice', action: 'USER_CREATED', entityType: 'User', entityId: 1, details: null, createdAt: '2026-01-01T00:00:00Z' },
    ];
    vi.spyOn(auditService, 'listAudit').mockReturnValue(of(entries));

    component.selectTab('audit');
    expect(component.tab()).toBe('audit');
    expect(auditService.listAudit).toHaveBeenCalledTimes(1);
    expect(component.auditLog()).toEqual(entries);

    component.selectTab('users');
    component.selectTab('audit');
    expect(auditService.listAudit).toHaveBeenCalledTimes(1);
  });

  it('searchAudit sends the filter form values, converting dates to ISO instants', () => {
    vi.spyOn(auditService, 'listAudit').mockReturnValue(of([]));
    component.auditFilterForm.setValue({
      actor: ' alice ',
      action: 'USER_ROLE_CHANGED',
      entityType: '',
      from: '2026-01-01T00:00',
      to: '',
    });

    component.searchAudit();

    expect(auditService.listAudit).toHaveBeenCalledWith({
      actor: 'alice',
      action: 'USER_ROLE_CHANGED',
      entityType: undefined,
      from: new Date('2026-01-01T00:00').toISOString(),
      to: undefined,
    });
  });

  it('resetAuditFilters clears the form and re-searches', () => {
    vi.spyOn(auditService, 'listAudit').mockReturnValue(of([]));
    component.auditFilterForm.patchValue({ actor: 'alice' });

    component.resetAuditFilters();

    expect(component.auditFilterForm.getRawValue().actor).toBe('');
    expect(auditService.listAudit).toHaveBeenCalledWith({
      actor: undefined,
      action: undefined,
      entityType: undefined,
      from: undefined,
      to: undefined,
    });
  });

  it('createUser does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(userService, 'createUser');
    component.newUserForm.patchValue({ email: 'not-an-email' });

    component.createUser();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createUser submits the form with partnerId for an operator, appends the new user, and resets the form', () => {
    const created: UserResponse = { id: 2, username: 'newop', email: 'newop@example.com', role: 'OPERATOR', partnerId: 9, createdAt: '2026-01-02T00:00:00Z' };
    vi.spyOn(userService, 'createUser').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');
    component.newUserForm.setValue({
      username: 'newop',
      email: 'newop@example.com',
      password: 'password123',
      role: 'OPERATOR',
      partnerId: 9,
    });

    component.createUser();

    expect(userService.createUser).toHaveBeenCalledWith({
      username: 'newop',
      email: 'newop@example.com',
      password: 'password123',
      role: 'OPERATOR',
      partnerId: 9,
    });
    expect(component.users()).toEqual([existingUser, created]);
    expect(successSpy).toHaveBeenCalledWith("Account 'newop' created as OPERATOR.");
    expect(component.newUserForm.getRawValue().username).toBe('');
  });

  it('createUser strips partnerId when the role is not OPERATOR', () => {
    const created: UserResponse = { id: 3, username: 'sup1', email: 'sup1@example.com', role: 'SUPPORT', partnerId: null, createdAt: '2026-01-02T00:00:00Z' };
    vi.spyOn(userService, 'createUser').mockReturnValue(of(created));
    component.newUserForm.setValue({
      username: 'sup1',
      email: 'sup1@example.com',
      password: 'password123',
      role: 'SUPPORT',
      partnerId: 9, // stray value from a prior OPERATOR selection, must not leak through
    });

    component.createUser();

    expect(userService.createUser).toHaveBeenCalledWith(
      expect.objectContaining({ role: 'SUPPORT', partnerId: null }),
    );
  });

  it('requestRoleChange does nothing when the selected role matches the current role', () => {
    const event = { target: { value: 'CUSTOMER' } } as unknown as Event;

    component.requestRoleChange(existingUser, event);

    expect(component.pendingRoleChange()).toBeNull();
  });

  it('requestRoleChange stages the change without submitting it', () => {
    const updateSpy = vi.spyOn(userService, 'updateRole');
    const event = { target: { value: 'SUPPORT' } } as unknown as Event;

    component.requestRoleChange(existingUser, event);

    expect(component.pendingRoleChange()).toEqual({ user: existingUser, role: 'SUPPORT' });
    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('cancelRoleChange clears the staged change without submitting it', () => {
    const updateSpy = vi.spyOn(userService, 'updateRole');
    component.requestRoleChange(existingUser, { target: { value: 'SUPPORT' } } as unknown as Event);

    component.cancelRoleChange();

    expect(component.pendingRoleChange()).toBeNull();
    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('confirmRoleChange submits the staged change and updates the user in place', () => {
    const updated: UserResponse = { ...existingUser, role: 'SUPPORT' };
    vi.spyOn(userService, 'updateRole').mockReturnValue(of(updated));
    const successSpy = vi.spyOn(notifications, 'success');
    component.requestRoleChange(existingUser, { target: { value: 'SUPPORT' } } as unknown as Event);

    component.confirmRoleChange();

    expect(userService.updateRole).toHaveBeenCalledWith(1, { role: 'SUPPORT' });
    expect(component.users()).toEqual([updated]);
    expect(successSpy).toHaveBeenCalledWith('alice is now SUPPORT.');
    expect(component.pendingRoleChange()).toBeNull();
  });

  it('confirmRoleChange clears the staged change even when the request is rejected (e.g. self-role-change)', () => {
    vi.spyOn(userService, 'updateRole').mockReturnValue(throwError(() => new Error('409')));
    component.requestRoleChange(existingUser, { target: { value: 'SUPPORT' } } as unknown as Event);

    component.confirmRoleChange();

    expect(component.pendingRoleChange()).toBeNull();
    expect(component.users()).toEqual([existingUser]);
  });

  it('confirmRoleChange does nothing when there is no staged change', () => {
    const updateSpy = vi.spyOn(userService, 'updateRole');

    component.confirmRoleChange();

    expect(updateSpy).not.toHaveBeenCalled();
  });

  describe('partners tab', () => {
    it('selectTab("partners") lazily loads the partner list only the first time', () => {
      vi.spyOn(partnerService, 'listPartners').mockReturnValue(of([existingPartner]));

      component.selectTab('partners');
      expect(component.tab()).toBe('partners');
      expect(partnerService.listPartners).toHaveBeenCalledTimes(1);
      expect(component.partners()).toEqual([existingPartner]);

      component.selectTab('users');
      component.selectTab('partners');
      expect(partnerService.listPartners).toHaveBeenCalledTimes(1);
    });

    it('createPartner does nothing while the form is invalid', () => {
      const createSpy = vi.spyOn(partnerService, 'createPartner');
      component.newPartnerForm.patchValue({ contactEmail: 'not-an-email' });

      component.createPartner();

      expect(createSpy).not.toHaveBeenCalled();
    });

    it('createPartner submits the form, appends the new partner, and resets the form', () => {
      vi.spyOn(partnerService, 'createPartner').mockReturnValue(of(existingPartner));
      const successSpy = vi.spyOn(notifications, 'success');
      component.newPartnerForm.setValue({ name: 'Acme Transit', contactEmail: 'ops@acme.example', commissionRate: 0.1 });

      component.createPartner();

      expect(partnerService.createPartner).toHaveBeenCalledWith({ name: 'Acme Transit', contactEmail: 'ops@acme.example', commissionRate: 0.1 });
      expect(component.partners()).toEqual([existingPartner]);
      expect(successSpy).toHaveBeenCalledWith("Partner 'Acme Transit' created (PENDING).");
      expect(component.newPartnerForm.getRawValue().name).toBe('');
    });

    it('requestPartnerStatusChange does nothing when the selected status matches the current one', () => {
      const event = { target: { value: 'PENDING' } } as unknown as Event;

      component.requestPartnerStatusChange(existingPartner, event);

      expect(component.pendingPartnerStatusChange()).toBeNull();
    });

    it('confirmPartnerStatusChange submits the staged change and updates the partner in place', () => {
      const updated: PartnerResponse = { ...existingPartner, status: 'ACTIVE' };
      vi.spyOn(partnerService, 'updateStatus').mockReturnValue(of(updated));
      component.partners.set([existingPartner]);
      component.requestPartnerStatusChange(existingPartner, { target: { value: 'ACTIVE' } } as unknown as Event);

      component.confirmPartnerStatusChange();

      expect(partnerService.updateStatus).toHaveBeenCalledWith(9, 'ACTIVE');
      expect(component.partners()).toEqual([updated]);
      expect(component.pendingPartnerStatusChange()).toBeNull();
    });

    it('selectPartner loads that partner\'s credentials and webhooks, clearing any previously shown secret', () => {
      const credential: PartnerCredentialResponse = {
        id: 1, partnerId: 9, clientId: 'pk_abc', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, revokedAt: null,
      };
      const webhook: PartnerWebhookResponse = {
        id: 1, partnerId: 9, url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
      };
      vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([credential]));
      vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([webhook]));

      component.selectPartner(existingPartner);

      expect(component.selectedPartnerId()).toBe(9);
      expect(partnerService.listCredentials).toHaveBeenCalledWith(9);
      expect(partnerService.listWebhooks).toHaveBeenCalledWith(9);
      expect(component.credentials()).toEqual([credential]);
      expect(component.webhooks()).toEqual([webhook]);
    });

    it('issueCredential shows the secret once and refreshes the credential list', () => {
      component.partners.set([existingPartner]);
      vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([]));
      vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([]));
      component.selectPartner(existingPartner);
      const issued: PartnerCredentialIssuedResponse = { id: 1, partnerId: 9, clientId: 'pk_abc', clientSecret: 'raw-secret', createdAt: '2026-01-01T00:00:00Z' };
      vi.spyOn(partnerService, 'issueCredential').mockReturnValue(of(issued));

      component.issueCredential();

      expect(component.justIssuedSecret()).toEqual({ clientId: 'pk_abc', clientSecret: 'raw-secret' });
      expect(partnerService.listCredentials).toHaveBeenCalledTimes(2); // once on select, once on refresh after issuing

      component.dismissIssuedSecret();
      expect(component.justIssuedSecret()).toBeNull();
    });

    it('revokeCredential calls the service and refreshes the credential list', () => {
      component.partners.set([existingPartner]);
      vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([]));
      vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([]));
      component.selectPartner(existingPartner);
      vi.spyOn(partnerService, 'revokeCredential').mockReturnValue(of(undefined));
      const credential: PartnerCredentialResponse = {
        id: 1, partnerId: 9, clientId: 'pk_abc', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, revokedAt: null,
      };

      component.revokeCredential(credential);

      expect(partnerService.revokeCredential).toHaveBeenCalledWith(1);
      expect(partnerService.listCredentials).toHaveBeenCalledTimes(2);
    });

    it('registerWebhook shows the secret once, resets the form, and refreshes the webhook list', () => {
      component.partners.set([existingPartner]);
      vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([]));
      vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([]));
      component.selectPartner(existingPartner);
      const issued: PartnerWebhookIssuedResponse = {
        id: 1, partnerId: 9, url: 'https://partner.example/hook', secret: 'raw-secret', eventType: 'BOOKING_CANCELLED', createdAt: '2026-01-01T00:00:00Z',
      };
      vi.spyOn(partnerService, 'registerWebhook').mockReturnValue(of(issued));
      component.newWebhookForm.setValue({ url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED' });

      component.registerWebhook();

      expect(component.justRegisteredWebhookSecret()).toEqual({ url: 'https://partner.example/hook', secret: 'raw-secret' });
      expect(component.newWebhookForm.getRawValue().url).toBe('');
      expect(partnerService.listWebhooks).toHaveBeenCalledTimes(2);

      component.dismissWebhookSecret();
      expect(component.justRegisteredWebhookSecret()).toBeNull();
    });

    it('toggleWebhookStatus flips ACTIVE to DISABLED and updates the webhook in place', () => {
      component.partners.set([existingPartner]);
      vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([]));
      vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([]));
      component.selectPartner(existingPartner);
      const webhook: PartnerWebhookResponse = {
        id: 1, partnerId: 9, url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
      };
      const updated: PartnerWebhookResponse = { ...webhook, status: 'DISABLED' };
      vi.spyOn(partnerService, 'updateWebhookStatus').mockReturnValue(of(updated));
      component.webhooks.set([webhook]);

      component.toggleWebhookStatus(webhook);

      expect(partnerService.updateWebhookStatus).toHaveBeenCalledWith(1, 'DISABLED');
      expect(component.webhooks()).toEqual([updated]);
    });
  });

  describe('promos tab', () => {
    it('selectTab("promos") lazily loads the promo code list only the first time', () => {
      vi.spyOn(promoService, 'listPromoCodes').mockReturnValue(of([existingPromoCode]));

      component.selectTab('promos');
      expect(component.tab()).toBe('promos');
      expect(promoService.listPromoCodes).toHaveBeenCalledTimes(1);
      expect(component.promoCodes()).toEqual([existingPromoCode]);

      component.selectTab('users');
      component.selectTab('promos');
      expect(promoService.listPromoCodes).toHaveBeenCalledTimes(1);
    });

    it('createPromoCode does nothing while the form is invalid', () => {
      const createSpy = vi.spyOn(promoService, 'createPromoCode');
      component.newPromoForm.patchValue({ code: '' });

      component.createPromoCode();

      expect(createSpy).not.toHaveBeenCalled();
    });

    it('createPromoCode uppercases the code, converts dates to ISO instants, prepends the result, and resets the form', () => {
      vi.spyOn(promoService, 'createPromoCode').mockReturnValue(of(existingPromoCode));
      const successSpy = vi.spyOn(notifications, 'success');
      component.newPromoForm.setValue({
        code: 'save20',
        discountType: 'PERCENTAGE',
        discountValue: 20,
        validFrom: '2026-01-01T00:00',
        validTo: '2026-12-31T00:00',
        maxRedemptions: 100,
      });

      component.createPromoCode();

      expect(promoService.createPromoCode).toHaveBeenCalledWith({
        code: 'SAVE20',
        discountType: 'PERCENTAGE',
        discountValue: 20,
        validFrom: new Date('2026-01-01T00:00').toISOString(),
        validTo: new Date('2026-12-31T00:00').toISOString(),
        maxRedemptions: 100,
      });
      expect(component.promoCodes()).toEqual([existingPromoCode]);
      expect(successSpy).toHaveBeenCalledWith("Promo code 'SAVE20' created.");
      expect(component.newPromoForm.getRawValue().code).toBe('');
    });

    it('createPromoCode sends null maxRedemptions when left blank', () => {
      vi.spyOn(promoService, 'createPromoCode').mockReturnValue(of(existingPromoCode));
      component.newPromoForm.setValue({
        code: 'SAVE20',
        discountType: 'FIXED_AMOUNT',
        discountValue: 5,
        validFrom: '2026-01-01T00:00',
        validTo: '2026-12-31T00:00',
        maxRedemptions: null,
      });

      component.createPromoCode();

      expect(promoService.createPromoCode).toHaveBeenCalledWith(
        expect.objectContaining({ maxRedemptions: null }),
      );
    });

    it('togglePromoStatus flips active to inactive and updates the promo code in place', () => {
      const updated: PromoCodeResponse = { ...existingPromoCode, active: false };
      vi.spyOn(promoService, 'updateStatus').mockReturnValue(of(updated));
      const successSpy = vi.spyOn(notifications, 'success');
      component.promoCodes.set([existingPromoCode]);

      component.togglePromoStatus(existingPromoCode);

      expect(promoService.updateStatus).toHaveBeenCalledWith(5, false);
      expect(component.promoCodes()).toEqual([updated]);
      expect(successSpy).toHaveBeenCalledWith("Promo code 'SAVE20' is now inactive.");
    });
  });

  describe('users tab template', () => {
    it('shows the partner picker only when the new-user role is OPERATOR', () => {
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('#partnerId')).not.toBeNull(); // OPERATOR is the form's default

      component.newUserForm.controls.role.setValue('SUPPORT');
      fixture.detectChanges();
      expect(el.querySelector('#partnerId')).toBeNull();

      component.newUserForm.controls.role.setValue('OPERATOR');
      fixture.detectChanges();
      expect(el.querySelector('#partnerId')).not.toBeNull();
    });

    it('renders a confirm dialog when a role change is requested, and Confirm submits it', () => {
      vi.spyOn(userService, 'updateRole').mockReturnValue(of({ ...existingUser, role: 'SUPPORT' }));
      const el = fixture.nativeElement as HTMLElement;
      const select = el.querySelector('[aria-label="Role for alice"]') as HTMLSelectElement;
      select.value = 'SUPPORT';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const confirmRow = el.querySelector('[aria-label="Confirm role change"]')!;
      expect(confirmRow.textContent).toContain('Change alice to SUPPORT?');

      const buttons = confirmRow.querySelectorAll<HTMLButtonElement>('button');
      buttons[1].click(); // Confirm

      expect(userService.updateRole).toHaveBeenCalledWith(1, { role: 'SUPPORT' });
    });

    it('Cancel in the confirm dialog discards the staged role change without submitting it', () => {
      const updateSpy = vi.spyOn(userService, 'updateRole');
      const el = fixture.nativeElement as HTMLElement;
      const select = el.querySelector('[aria-label="Role for alice"]') as HTMLSelectElement;
      select.value = 'SUPPORT';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const buttons = el.querySelector('[aria-label="Confirm role change"]')!.querySelectorAll<HTMLButtonElement>('button');
      buttons[0].click(); // Cancel
      fixture.detectChanges();

      expect(el.querySelector('[aria-label="Confirm role change"]')).toBeNull();
      expect(updateSpy).not.toHaveBeenCalled();
    });
  });

  describe('partners tab template', () => {
    beforeEach(() => {
      vi.spyOn(partnerService, 'listPartners').mockReturnValue(of([existingPartner]));
      component.selectTab('partners');
      fixture.detectChanges();
    });

    it('renders the partner list with a status select and a Manage button', () => {
      const row = (fixture.nativeElement as HTMLElement).querySelector('table.data-table tbody tr')!;
      expect(row.textContent).toContain('Acme Transit');
      expect(row.querySelector('button')!.textContent!.trim()).toBe('Manage');
    });

    it('shows a confirm dialog for a partner status change without submitting it', () => {
      const updateSpy = vi.spyOn(partnerService, 'updateStatus');
      const el = fixture.nativeElement as HTMLElement;
      const select = el.querySelector('[aria-label="Status for Acme Transit"]') as HTMLSelectElement;
      select.value = 'ACTIVE';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(el.querySelector('[aria-label="Confirm partner status change"]')!.textContent).toContain(
        'Change Acme Transit to ACTIVE?',
      );
      expect(updateSpy).not.toHaveBeenCalled();
    });

    it('renders "No partners onboarded yet" when the list is empty', () => {
      component.partners.set([]);
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('No partners onboarded yet.');
    });

    describe('with a partner selected', () => {
      beforeEach(() => {
        vi.spyOn(partnerService, 'listCredentials').mockReturnValue(of([]));
        vi.spyOn(partnerService, 'listWebhooks').mockReturnValue(of([]));
        component.selectPartner(existingPartner);
        fixture.detectChanges();
      });

      it('renders the partner-detail header and the close button clears it', () => {
        const el = fixture.nativeElement as HTMLElement;
        expect(el.querySelector('.detail-header h3')!.textContent).toContain('Acme Transit');

        (el.querySelector('[aria-label="Close partner detail"]') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(el.querySelector('.detail-header')).toBeNull();
      });

      it('shows the empty states for credentials and webhooks', () => {
        const text = (fixture.nativeElement as HTMLElement).textContent;
        expect(text).toContain('No credentials issued yet.');
        expect(text).toContain('No webhooks registered yet.');
      });

      it('renders the just-issued credential secret once, and dismiss hides it', () => {
        const issued: PartnerCredentialIssuedResponse = {
          id: 1, partnerId: 9, clientId: 'pk_abc', clientSecret: 'raw-secret', createdAt: '2026-01-01T00:00:00Z',
        };
        vi.spyOn(partnerService, 'issueCredential').mockReturnValue(of(issued));

        component.issueCredential();
        fixture.detectChanges();

        const card = cardContaining('API credentials');
        expect(card.querySelector('.secret-banner')!.textContent).toContain('raw-secret');

        component.dismissIssuedSecret();
        fixture.detectChanges();

        expect(card.querySelector('.secret-banner')).toBeNull();
      });

      it('renders a credential row and only shows Revoke for an ACTIVE credential', () => {
        const active: PartnerCredentialResponse = {
          id: 1, partnerId: 9, clientId: 'pk_active', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, revokedAt: null,
        };
        const revoked: PartnerCredentialResponse = {
          id: 2, partnerId: 9, clientId: 'pk_revoked', status: 'REVOKED', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: '2026-01-02T00:00:00Z', revokedAt: '2026-01-03T00:00:00Z',
        };
        component.credentials.set([active, revoked]);
        fixture.detectChanges();

        const rows = cardContaining('API credentials').querySelectorAll('tbody tr');
        expect(rows.length).toBe(2);
        expect(rows[0].querySelector('button')).not.toBeNull();
        expect(rows[1].querySelector('button')).toBeNull();
      });

      it('renders the just-registered webhook secret once, and dismiss hides it', () => {
        const issued: PartnerWebhookIssuedResponse = {
          id: 1, partnerId: 9, url: 'https://partner.example/hook', secret: 'raw-secret', eventType: 'BOOKING_CANCELLED', createdAt: '2026-01-01T00:00:00Z',
        };
        vi.spyOn(partnerService, 'registerWebhook').mockReturnValue(of(issued));
        component.newWebhookForm.setValue({ url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED' });

        component.registerWebhook();
        fixture.detectChanges();

        const card = cardContaining('webhooks');
        expect(card.querySelector('.secret-banner')!.textContent).toContain('raw-secret');

        component.dismissWebhookSecret();
        fixture.detectChanges();

        expect(card.querySelector('.secret-banner')).toBeNull();
      });

      it('the webhook toggle button label reflects the current status', () => {
        const active: PartnerWebhookResponse = {
          id: 1, partnerId: 9, url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
        };
        component.webhooks.set([active]);
        fixture.detectChanges();

        const button = cardContaining('webhooks').querySelector('tbody tr button') as HTMLButtonElement;
        expect(button.textContent!.trim()).toBe('Disable');
      });
    });
  });

  describe('promos tab template', () => {
    it('renders a promo row with percentage discount formatting and an Active badge', () => {
      vi.spyOn(promoService, 'listPromoCodes').mockReturnValue(of([existingPromoCode]));
      component.selectTab('promos');
      fixture.detectChanges();

      const row = (fixture.nativeElement as HTMLElement).querySelector('table.data-table tbody tr')!;
      expect(row.textContent).toContain('20%');
      expect(row.querySelector('.badge')!.textContent!.trim()).toBe('Active');
    });

    it('formats a FIXED_AMOUNT discount as a plain number, not a percentage', () => {
      const fixedPromo: PromoCodeResponse = { ...existingPromoCode, discountType: 'FIXED_AMOUNT', discountValue: 15, active: false };
      vi.spyOn(promoService, 'listPromoCodes').mockReturnValue(of([fixedPromo]));
      component.selectTab('promos');
      fixture.detectChanges();

      const row = (fixture.nativeElement as HTMLElement).querySelector('table.data-table tbody tr')!;
      expect(row.textContent).toContain('15.00');
      expect(row.querySelector('.badge')!.textContent!.trim()).toBe('Inactive');
    });

    it('renders "No promo codes created yet" when the list is empty', () => {
      vi.spyOn(promoService, 'listPromoCodes').mockReturnValue(of([]));
      component.selectTab('promos');
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('No promo codes created yet.');
    });
  });

  describe('audit tab template', () => {
    it('renders an audit row, showing the entity id only when present', () => {
      const entries: AuditLogResponse[] = [
        { id: 1, actorUsername: 'alice', action: 'USER_ROLE_CHANGED', entityType: 'User', entityId: 42, details: 'role -> SUPPORT', createdAt: '2026-01-01T00:00:00Z' },
      ];
      vi.spyOn(auditService, 'listAudit').mockReturnValue(of(entries));
      component.selectTab('audit');
      fixture.detectChanges();

      const row = (fixture.nativeElement as HTMLElement).querySelector('table.data-table tbody tr')!;
      expect(row.textContent).toContain('#42');
      expect(row.textContent).toContain('role -> SUPPORT');
    });

    it('renders "No entries match these filters" when the audit search comes back empty', () => {
      vi.spyOn(auditService, 'listAudit').mockReturnValue(of([]));
      component.selectTab('audit');
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('No entries match these filters.');
    });
  });
});
