import { FormControl } from '@angular/forms';
import { luhnValidator } from './luhn.validator';

describe('luhnValidator', () => {
  const control = (value: string) => {
    const c = new FormControl(value);
    c.setValidators(luhnValidator());
    c.updateValueAndValidity();
    return c;
  };

  it('passes for a blank value', () => {
    expect(control('').errors).toBeNull();
  });

  it('passes for a valid Luhn number (Stripe test approve card)', () => {
    expect(control('4242 4242 4242 4242').errors).toBeNull();
  });

  it('passes for a valid Luhn number (Stripe test decline card)', () => {
    expect(control('4000 0000 0000 0002').errors).toBeNull();
  });

  it('fails for a number that fails the checksum', () => {
    expect(control('4242 4242 4242 4241').errors).toEqual({ luhn: true });
  });

  it('ignores spaces and dashes in the input', () => {
    expect(control('4242-4242-4242-4242').errors).toBeNull();
  });
});
