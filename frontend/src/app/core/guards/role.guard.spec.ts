import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => localStorage.clear());

  function runGuard(role: string) {
    return TestBed.runInInjectionContext(() =>
      roleGuard(
        { data: { role } } as unknown as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );
  }

  it('redirects to login when not authenticated', () => {
    const auth = TestBed.inject(AuthService);
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(false);

    const result = runGuard('ADMIN') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
  });

  it('allows navigation when authenticated and holding the required role', () => {
    const auth = TestBed.inject(AuthService);
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true);
    vi.spyOn(auth, 'hasRole').mockReturnValue(true);

    expect(runGuard('ADMIN')).toBe(true);
  });

  it('redirects to /search when authenticated but missing the required role', () => {
    const auth = TestBed.inject(AuthService);
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true);
    vi.spyOn(auth, 'hasRole').mockReturnValue(false);

    const result = runGuard('ADMIN') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/search');
  });
});
