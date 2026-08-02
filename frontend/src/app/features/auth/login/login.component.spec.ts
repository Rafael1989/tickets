import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { LoginComponent } from './login.component';

function fakeJwt(sub: string, roles: string[]): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'HS512' })}.${base64url({ sub, roles, iat: 1, exp: 9999999999 })}.sig`;
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: AuthService;
  let router: Router;

  async function createComponent(queryParams: Record<string, string> = {}) {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
      ],
    }).compileComponents();

    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => localStorage.clear());

  it('shows no notice when the visitor came to log in on their own', async () => {
    await createComponent();

    expect(component.redirectMessage).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.redirect-notice')).toBeNull();
  });

  it('explains why a visitor bounced out of checkout is being asked to sign in', async () => {
    await createComponent({ redirectTo: '/checkout' });

    const notice = (fixture.nativeElement as HTMLElement).querySelector('.redirect-notice');
    expect(notice).not.toBeNull();
    expect(notice?.textContent).toContain('finish your booking');
  });

  it('shows a generic notice for any other guarded destination', async () => {
    await createComponent({ redirectTo: '/bookings' });

    const notice = (fixture.nativeElement as HTMLElement).querySelector('.redirect-notice');
    expect(notice?.textContent).toContain('Please sign in to continue');
  });

  it('carries redirectTo onto the Register link so signing up mid-checkout still returns there', async () => {
    await createComponent({ redirectTo: '/checkout' });

    const registerLink = (fixture.nativeElement as HTMLElement).querySelector('a[href*="register"]');
    expect(registerLink?.getAttribute('href')).toContain('redirectTo=%2Fcheckout');
  });

  it('returns the customer to where they were headed after a successful login', async () => {
    await createComponent({ redirectTo: '/checkout' });
    vi.spyOn(auth, 'login').mockReturnValue(of({ accessToken: 't', tokenType: 'Bearer', expiresInSeconds: 900 }));
    component.form.setValue({ username: 'alice', password: 'pw' });

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/checkout');
  });

  /**
   * Logs in through the real AuthService + HttpTestingController rather than a stubbed login():
   * the role-aware landing depends on the token actually being stored by login()'s tap, which a
   * mocked return value would skip entirely — leaving homePath() stale and the assertion hollow.
   */
  function loginAsAndFlush(username: string, roles: string[]) {
    component.form.setValue({ username, password: 'pw' });
    component.submit();

    const httpMock = TestBed.inject(HttpTestingController);
    httpMock
      .expectOne('/api/login')
      .flush({ accessToken: fakeJwt(username, roles), tokenType: 'Bearer', expiresInSeconds: 900 });
  }

  it('falls back to search for a customer when there was no guarded destination', async () => {
    await createComponent();

    loginAsAndFlush('alice', ['CUSTOMER']);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/search');
  });

  it('lands staff on their own portal rather than the customer search page', async () => {
    await createComponent();

    loginAsAndFlush('dave', ['ADMIN']);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });
});
