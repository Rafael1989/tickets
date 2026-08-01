import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { FareRuleResponse } from '../../../core/models/fare-rule.model';
import { FareRuleService } from '../../../core/services/fare-rule.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FareMatrixComponent } from './fare-matrix.component';

describe('FareMatrixComponent', () => {
  let fixture: ComponentFixture<FareMatrixComponent>;
  let component: FareMatrixComponent;
  let fareRuleService: FareRuleService;
  let notifications: NotificationService;

  const existingRule: FareRuleResponse = {
    id: 9,
    routeId: 1,
    seatClass: 'business',
    validFrom: '2026-12-01T00:00:00Z',
    validTo: '2026-12-31T23:59:59Z',
    surchargeRate: 0.2,
  };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [FareMatrixComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fareRuleService = TestBed.inject(FareRuleService);
    vi.spyOn(fareRuleService, 'listFareRulesForRoute').mockReturnValue(of([existingRule]));
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(FareMatrixComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('routeId', 1);
    fixture.detectChanges();
  }

  async function selectFile(content: string, filename = 'rules.csv'): Promise<void> {
    const file = new File([content], filename, { type: 'text/csv' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    component.onFileSelected({ target: input } as unknown as Event);
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  beforeEach(async () => await createComponent());

  it('loads existing fare rules for the route on init', () => {
    expect(fareRuleService.listFareRulesForRoute).toHaveBeenCalledWith(1);
    expect(component.fareRules()).toEqual([existingRule]);
  });

  it('createFareRule does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(fareRuleService, 'createFareRule');
    component.ruleForm.patchValue({ seatClass: '' });

    component.createFareRule();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createFareRule submits the form and appends the new rule', () => {
    const created: FareRuleResponse = { ...existingRule, id: 10, seatClass: 'economy' };
    vi.spyOn(fareRuleService, 'createFareRule').mockReturnValue(of(created));
    component.ruleForm.setValue({
      seatClass: 'economy',
      validFrom: '2026-12-01T00:00',
      validTo: '2026-12-31T00:00',
      surchargeRate: 0.1,
    });

    component.createFareRule();

    expect(fareRuleService.createFareRule).toHaveBeenCalledWith(
      expect.objectContaining({ routeId: 1, seatClass: 'economy', surchargeRate: 0.1 }),
    );
    expect(component.fareRules()).toEqual([existingRule, created]);
  });

  it('parses a valid CSV into an error-free preview', async () => {
    await selectFile(
      'seatClass,validFrom,validTo,surchargeRate\n' +
        'business,2026-12-01T00:00:00Z,2026-12-31T23:59:59Z,0.20\n' +
        'economy,2026-12-01T00:00:00Z,2026-12-31T23:59:59Z,0.10',
    );

    expect(component.previewRows()).toHaveLength(2);
    expect(component.hasPreviewErrors()).toBe(false);
  });

  it('flags a row with an invalid date range', async () => {
    await selectFile(
      'seatClass,validFrom,validTo,surchargeRate\n' + 'business,2026-12-31T00:00:00Z,2026-12-01T00:00:00Z,0.20',
    );

    expect(component.previewRows()[0].error).toContain('validFrom must be before validTo');
    expect(component.hasPreviewErrors()).toBe(true);
  });

  it('flags a row with a non-numeric surcharge rate', async () => {
    await selectFile(
      'seatClass,validFrom,validTo,surchargeRate\n' + 'business,2026-12-01T00:00:00Z,2026-12-31T00:00:00Z,abc',
    );

    expect(component.previewRows()[0].error).toContain('not a number');
  });

  it('rejects a CSV with the wrong header and clears the preview', async () => {
    const errorSpy = vi.spyOn(notifications, 'error');

    await selectFile('class,from,to,rate\nbusiness,2026-12-01,2026-12-31,0.2');

    expect(component.previewRows()).toHaveLength(0);
    expect(errorSpy).toHaveBeenCalledWith(expect.stringContaining('CSV header must be exactly'));
  });

  it('confirmUpload is blocked while any preview row has an error', async () => {
    await selectFile(
      'seatClass,validFrom,validTo,surchargeRate\n' + 'business,2026-12-31T00:00:00Z,2026-12-01T00:00:00Z,0.20',
    );
    const bulkSpy = vi.spyOn(fareRuleService, 'bulkCreateFareRules');

    component.confirmUpload();

    expect(bulkSpy).not.toHaveBeenCalled();
  });

  it('confirmUpload submits every valid row and appends the results', async () => {
    await selectFile(
      'seatClass,validFrom,validTo,surchargeRate\n' +
        'business,2026-12-01T00:00:00Z,2026-12-31T23:59:59Z,0.20\n' +
        'economy,2026-12-01T00:00:00Z,2026-12-31T23:59:59Z,0.10',
    );
    const created: FareRuleResponse[] = [
      { ...existingRule, id: 11, seatClass: 'business' },
      { ...existingRule, id: 12, seatClass: 'economy', surchargeRate: 0.1 },
    ];
    vi.spyOn(fareRuleService, 'bulkCreateFareRules').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');

    component.confirmUpload();

    expect(fareRuleService.bulkCreateFareRules).toHaveBeenCalledWith([
      expect.objectContaining({ routeId: 1, seatClass: 'business' }),
      expect.objectContaining({ routeId: 1, seatClass: 'economy' }),
    ]);
    expect(component.fareRules()).toEqual([existingRule, ...created]);
    expect(component.previewRows()).toHaveLength(0);
    expect(successSpy).toHaveBeenCalledWith(expect.stringContaining('2 fare rule(s)'));
  });

  it('clearPreview resets the preview state', async () => {
    await selectFile('seatClass,validFrom,validTo,surchargeRate\nbusiness,2026-12-01T00:00:00Z,2026-12-31T00:00:00Z,0.2');

    component.clearPreview();

    expect(component.previewRows()).toHaveLength(0);
    expect(component.previewFileName()).toBeNull();
  });
});
