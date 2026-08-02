import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Loose, format-only checks per ID type — enough to catch obvious typos (wrong length, stray symbols) without pretending to validate against any real national ID scheme. */
const ID_PATTERNS: Record<string, RegExp> = {
  passport: /^[A-Za-z0-9]{6,9}$/,
  national_id: /^[A-Za-z0-9]{5,20}$/,
  driver_license: /^[A-Za-z0-9-]{5,20}$/,
};

/**
 * Validates the control's value against the format for the sibling idType
 * control's current value. Must be attached to the idNumber control inside
 * a FormGroup that also has an idType control; re-run
 * idNumber.updateValueAndValidity() whenever idType changes since Angular
 * doesn't do that automatically for cross-field validators.
 */
export function idNumberValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '').trim();
    if (!value) {
      return null;
    }

    const idType = control.parent?.get('idType')?.value as string | undefined;
    const pattern = idType ? ID_PATTERNS[idType] : undefined;
    if (!pattern) {
      return null;
    }

    return pattern.test(value) ? null : { idFormat: true };
  };
}
