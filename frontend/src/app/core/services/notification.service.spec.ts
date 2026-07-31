import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts with no notifications', () => {
    expect(service.notifications()).toEqual([]);
  });

  it('error() shows an error notification', () => {
    service.error('Something broke');

    expect(service.notifications()).toHaveLength(1);
    expect(service.notifications()[0]).toMatchObject({ kind: 'error', message: 'Something broke' });
  });

  it('success() shows a success notification', () => {
    service.success('Saved');

    expect(service.notifications()[0]).toMatchObject({ kind: 'success', message: 'Saved' });
  });

  it('auto-dismisses a notification after its duration', () => {
    service.show('info', 'Heads up', 1000);
    expect(service.notifications()).toHaveLength(1);

    vi.advanceTimersByTime(1000);

    expect(service.notifications()).toEqual([]);
  });

  it('dismiss() removes only the matching notification', () => {
    service.error('first');
    service.error('second');
    const [first, second] = service.notifications();

    service.dismiss(first.id);

    expect(service.notifications()).toEqual([second]);
  });
});
