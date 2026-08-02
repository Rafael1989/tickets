import { Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

const TICK_INTERVAL_MS = 1000;

/**
 * Ticking countdown to an ISO timestamp (typically a seat hold's heldUntil).
 * Emits `expired` exactly once when the deadline passes — callers decide what
 * that means (block checkout, show a banner, etc.), this component only
 * tracks the clock.
 */
@Component({
  selector: 'tw-countdown',
  templateUrl: './countdown.component.html',
  styleUrl: './countdown.component.scss',
})
export class CountdownComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly expiresAt = input<string | null>(null);
  /** Drops the pill background/padding for tight spaces (e.g. inside a seat-map button) — the urgent/expired color cues still apply. */
  readonly bare = input(false);
  readonly expired = output<void>();

  private readonly now = signal(Date.now());
  private hasEmittedExpired = false;

  readonly remainingSeconds = computed(() => {
    const expiresAt = this.expiresAt();
    if (!expiresAt) {
      return null;
    }
    return Math.max(0, Math.round((Date.parse(expiresAt) - this.now()) / 1000));
  });

  readonly isExpired = computed(() => this.remainingSeconds() === 0);

  readonly isUrgent = computed(() => {
    const remaining = this.remainingSeconds();
    return remaining !== null && remaining > 0 && remaining <= 60;
  });

  readonly formatted = computed(() => {
    const remaining = this.remainingSeconds();
    if (remaining === null) {
      return null;
    }
    const minutes = Math.floor(remaining / 60);
    const seconds = remaining % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  });

  constructor() {
    interval(TICK_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.now.set(Date.now()));

    effect(() => {
      if (this.isExpired()) {
        if (!this.hasEmittedExpired) {
          this.hasEmittedExpired = true;
          this.expired.emit();
        }
      } else {
        // A fresh, later expiresAt (e.g. checkout re-fetching a renewed seat
        // hold) can arrive after a previous deadline already expired -
        // re-arm so that one can emit its own expired event too.
        this.hasEmittedExpired = false;
      }
    });
  }
}
