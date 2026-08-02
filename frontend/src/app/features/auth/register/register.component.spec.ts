import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let auth: AuthService;
  let router: Router;

  async function createComponent(queryParams: Record<string, string> = {}) {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
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
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(auth, 'register').mockReturnValue(of({}));

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillValidForm() {
    component.form.setValue({ username: 'alice', email: 'alice@example.com', password: 'password123' });
  }

  it('keeps redirectTo when handing off to the login page, so checkout resumes after signing up', async () => {
    await createComponent({ redirectTo: '/checkout' });
    fillValidForm();

    component.submit();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { redirectTo: '/checkout' } });
  });

  it('goes to a plain login page when there was no guarded destination', async () => {
    await createComponent();
    fillValidForm();

    component.submit();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {});
  });
});
