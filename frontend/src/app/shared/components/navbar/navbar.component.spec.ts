import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NavbarComponent } from './navbar.component';

function fakeJwt(sub: string, roles: string[] = ['CUSTOMER']): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'HS512' })}.${base64url({ sub, roles, iat: 1, exp: 9999999999 })}.sig`;
}

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let router: Router;

  afterEach(() => localStorage.clear());

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
  }

  it('shows log in / register links when not authenticated', async () => {
    localStorage.clear();
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Log in');
    expect(html).toContain('Register');
  });

  it('shows the username and a log out button when authenticated', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('alice'));
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('alice');
    expect(html).toContain('Log out');
  });

  it('logout() logs out and navigates to /search', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('alice'));
    await createComponent();
    const auth = TestBed.inject(AuthService);
    const logoutSpy = vi.spyOn(auth, 'logout');

    fixture.componentInstance.logout();

    expect(logoutSpy).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/search']);
  });

  it('shows no role-based links for a plain customer', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('alice', ['CUSTOMER']));
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).not.toContain('Operator');
    expect(html).not.toContain('Support');
    expect(html).not.toContain('Admin');
  });

  it('shows the Operator link for an operator', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('bob', ['OPERATOR']));
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Operator');
    expect(html).not.toContain('Support');
  });

  it('shows the Support link for a support agent', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('carol', ['SUPPORT']));
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Support');
  });

  it('shows the Admin link for an admin', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('dave', ['ADMIN']));
    await createComponent();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Admin');
  });

  it('clicking log out triggers logout via the DOM', async () => {
    localStorage.setItem('tw.accessToken', fakeJwt('alice'));
    await createComponent();

    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    button.click();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
  });
});
