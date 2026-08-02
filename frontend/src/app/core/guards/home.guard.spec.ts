import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { homeGuard } from './home.guard';

function fakeJwt(sub: string, roles: string[]): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'HS512' })}.${base64url({ sub, roles, iat: 1, exp: 9999999999 })}.sig`;
}

describe('homeGuard', () => {
  afterEach(() => localStorage.clear());

  function run(): UrlTree {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    return TestBed.runInInjectionContext(
      () => homeGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as UrlTree;
  }

  it('sends a signed-out visitor to the public search page', () => {
    localStorage.clear();
    expect(run().toString()).toBe('/search');
  });

  it('sends a customer to search', () => {
    localStorage.setItem('tw.accessToken', fakeJwt('alice', ['CUSTOMER']));
    expect(run().toString()).toBe('/search');
  });

  it('sends an operator to their own portal instead of the customer search page', () => {
    localStorage.setItem('tw.accessToken', fakeJwt('bob', ['OPERATOR']));
    expect(run().toString()).toBe('/operator');
  });

  it('sends a support agent to their own portal', () => {
    localStorage.setItem('tw.accessToken', fakeJwt('carol', ['SUPPORT']));
    expect(run().toString()).toBe('/support');
  });

  it('sends an admin to their own portal', () => {
    localStorage.setItem('tw.accessToken', fakeJwt('dave', ['ADMIN']));
    expect(run().toString()).toBe('/admin');
  });
});
