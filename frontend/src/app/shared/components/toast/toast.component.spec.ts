import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../core/services/notification.service';
import { ToastComponent } from './toast.component';

describe('ToastComponent', () => {
  let fixture: ComponentFixture<ToastComponent>;
  let notifications: NotificationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToastComponent],
    }).compileComponents();

    notifications = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(ToastComponent);
    fixture.detectChanges();
  });

  it('renders nothing when there are no notifications', () => {
    const html = (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
    expect(html).toBe('');
  });

  it('renders a notification message', () => {
    notifications.error('Something broke');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Something broke');
  });

  it('dismiss() removes the notification when clicked', () => {
    notifications.success('Saved');
    fixture.detectChanges();

    const toast = (fixture.nativeElement as HTMLElement).querySelector('.toast')!;
    toast.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
  });
});
