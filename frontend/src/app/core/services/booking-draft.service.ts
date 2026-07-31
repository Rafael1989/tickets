import { Injectable, signal } from '@angular/core';
import { ScheduleSearchResult, SeatResponse } from '../models/catalog.model';

export interface BookingDraft {
  schedule: ScheduleSearchResult;
  seats: SeatResponse[];
}

/**
 * Carries the schedule + selected seats from the seat-selection page to
 * checkout. In-memory only (lost on refresh) since checkout is a single
 * continuous flow from seat selection, not a resumable draft.
 */
@Injectable({ providedIn: 'root' })
export class BookingDraftService {
  private readonly draftSignal = signal<BookingDraft | null>(null);
  readonly draft = this.draftSignal.asReadonly();

  set(draft: BookingDraft): void {
    this.draftSignal.set(draft);
  }

  clear(): void {
    this.draftSignal.set(null);
  }
}
