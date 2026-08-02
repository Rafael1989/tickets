import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Standard Luhn (mod 10) checksum used by every major card network — catches
 * a mistyped digit before it ever reaches the payment gateway. Ignores
 * spaces/dashes so it works directly against the formatted "4242 4242 4242
 * 4242" display value. A blank value passes (pair with Validators.required
 * for mandatory fields).
 */
export function luhnValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const digits = String(control.value ?? '').replace(/\D/g, '');
    if (!digits) {
      return null;
    }

    let sum = 0;
    let shouldDouble = false;
    for (let i = digits.length - 1; i >= 0; i--) {
      let digit = Number(digits[i]);
      if (shouldDouble) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      shouldDouble = !shouldDouble;
    }

    return sum % 10 === 0 ? null : { luhn: true };
  };
}
