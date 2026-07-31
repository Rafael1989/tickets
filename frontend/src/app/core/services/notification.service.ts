import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'error' | 'success' | 'info';

export interface Notification {
  id: number;
  kind: NotificationKind;
  message: string;
}

let nextId = 1;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly notificationsSignal = signal<Notification[]>([]);
  readonly notifications = this.notificationsSignal.asReadonly();

  show(kind: NotificationKind, message: string, durationMs = 5000): void {
    const notification: Notification = { id: nextId++, kind, message };
    this.notificationsSignal.update((current) => [...current, notification]);
    setTimeout(() => this.dismiss(notification.id), durationMs);
  }

  error(message: string): void {
    this.show('error', message);
  }

  success(message: string): void {
    this.show('success', message);
  }

  dismiss(id: number): void {
    this.notificationsSignal.update((current) => current.filter((n) => n.id !== id));
  }
}
