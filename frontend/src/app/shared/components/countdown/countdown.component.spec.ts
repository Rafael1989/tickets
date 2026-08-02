import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CountdownComponent } from './countdown.component';

describe('CountdownComponent', () => {
  let fixture: ComponentFixture<CountdownComponent>;
  let component: CountdownComponent;

  async function createComponent(expiresAt: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CountdownComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CountdownComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('expiresAt', expiresAt);
    fixture.detectChanges();
  }

  it('renders nothing when there is no deadline', async () => {
    await createComponent(null);

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
    expect(component.formatted()).toBeNull();
  });

  it('shows a formatted mm:ss countdown for a future deadline', async () => {
    await createComponent(new Date(Date.now() + 90_000).toISOString());

    expect(component.formatted()).toBe('1:30');
    expect(component.isExpired()).toBe(false);
  });

  it('marks itself urgent inside the last minute', async () => {
    await createComponent(new Date(Date.now() + 45_000).toISOString());

    expect(component.isUrgent()).toBe(true);
  });

  it('applies the bare class when requested, for use in tight spaces', async () => {
    await createComponent(new Date(Date.now() + 90_000).toISOString());
    fixture.componentRef.setInput('bare', true);
    fixture.detectChanges();

    const span = (fixture.nativeElement as HTMLElement).querySelector('.countdown');
    expect(span?.classList.contains('bare')).toBe(true);
  });

  it('shows "Expired" and emits expired for an already-passed deadline', async () => {
    await TestBed.configureTestingModule({ imports: [CountdownComponent] }).compileComponents();
    fixture = TestBed.createComponent(CountdownComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('expiresAt', new Date(Date.now() - 1_000).toISOString());

    let emitted = false;
    component.expired.subscribe(() => (emitted = true));
    fixture.detectChanges();

    expect(component.isExpired()).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('Expired');
    expect(emitted).toBe(true);
  });
});
