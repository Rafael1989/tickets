import { FormBuilder } from '@angular/forms';
import { idNumberValidator } from './id-number.validator';

describe('idNumberValidator', () => {
  const fb = new FormBuilder();

  // Angular runs a control's own validators once at construction, before it's
  // attached to its parent group — so a validator that reads a sibling via
  // control.parent sees it as undefined on that very first pass. Re-running
  // it once mirrors what the app's idType.valueChanges subscription does.
  function groupWith(idType: string, idNumber: string) {
    const group = fb.group({
      idType: [idType],
      idNumber: [idNumber, idNumberValidator()],
    });
    group.controls.idNumber.updateValueAndValidity();
    return group;
  }

  it('passes for a blank value', () => {
    expect(groupWith('passport', '').controls.idNumber.errors).toBeNull();
  });

  it('passes a valid passport number', () => {
    expect(groupWith('passport', 'AB123456').controls.idNumber.errors).toBeNull();
  });

  it('fails a too-short passport number', () => {
    expect(groupWith('passport', 'AB12').controls.idNumber.errors).toEqual({ idFormat: true });
  });

  it('passes a valid national ID number', () => {
    expect(groupWith('national_id', '123456789').controls.idNumber.errors).toBeNull();
  });

  it('passes a valid driver license number containing a dash', () => {
    expect(groupWith('driver_license', 'DL-12345').controls.idNumber.errors).toBeNull();
  });

  it('fails a value with disallowed symbols', () => {
    expect(groupWith('passport', 'AB!23456').controls.idNumber.errors).toEqual({ idFormat: true });
  });

  it('passes for an unknown idType (no pattern to enforce)', () => {
    expect(groupWith('other', '!!!').controls.idNumber.errors).toBeNull();
  });

  it('re-validates against the updated pattern when idType changes', () => {
    const group = groupWith('national_id', 'AB!23456');
    expect(group.controls.idNumber.errors).toEqual({ idFormat: true });

    group.controls.idType.setValue('passport');
    group.controls.idNumber.updateValueAndValidity();

    expect(group.controls.idNumber.errors).toEqual({ idFormat: true });
  });
});
