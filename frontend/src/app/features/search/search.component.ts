import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RouteType, ScheduleSearchResult, ScheduleSortBy } from '../../core/models/catalog.model';
import { SearchService } from '../../core/services/search.service';

interface TypeOption {
  value: RouteType | '';
  label: string;
  icon: string;
}

const TYPE_ICONS: Record<RouteType, string> = {
  FLIGHT: '✈️',
  BUS: '🚌',
  TRAIN: '🚆',
  EVENT: '🎫',
};

@Component({
  selector: 'tw-search',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss',
})
export class SearchComponent {
  private readonly fb = inject(FormBuilder);
  private readonly searchService = inject(SearchService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly hasSearched = signal(false);
  readonly results = signal<ScheduleSearchResult[]>([]);
  readonly skeletonRows = [0, 1, 2];

  readonly typeOptions: TypeOption[] = [
    { value: '', label: 'Any', icon: '🌐' },
    { value: 'FLIGHT', label: 'Flight', icon: '✈️' },
    { value: 'BUS', label: 'Bus', icon: '🚌' },
    { value: 'TRAIN', label: 'Train', icon: '🚆' },
    { value: 'EVENT', label: 'Event', icon: '🎫' },
  ];

  readonly sortOptions: { value: ScheduleSortBy | ''; label: string }[] = [
    { value: '', label: 'Soonest departure' },
    { value: 'PRICE_ASC', label: 'Price: low to high' },
    { value: 'PRICE_DESC', label: 'Price: high to low' },
  ];

  readonly seatClassOptions = ['', 'economy', 'business', 'first'];

  readonly form = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<RouteType | ''>(''),
    origin: [''],
    destination: [''],
    venue: [''],
    departureDate: [''],
    minPrice: this.fb.nonNullable.control<number | null>(null),
    maxPrice: this.fb.nonNullable.control<number | null>(null),
    seatClass: [''],
    sortBy: this.fb.nonNullable.control<ScheduleSortBy | ''>(''),
  });

  readonly selectedType = toSignal(this.form.controls.type.valueChanges, {
    initialValue: this.form.controls.type.value,
  });

  search(): void {
    this.loading.set(true);
    this.searchService
      .search(this.form.getRawValue())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          this.results.set(results);
          this.hasSearched.set(true);
        },
      });
  }

  selectType(value: RouteType | ''): void {
    this.form.controls.type.setValue(value);
  }

  typeIcon(type: RouteType): string {
    return TYPE_ICONS[type];
  }
}
