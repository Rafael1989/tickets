import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RouteType, ScheduleSearchResult } from '../../core/models/catalog.model';
import { SearchService } from '../../core/services/search.service';

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

  readonly form = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<RouteType | ''>(''),
    origin: [''],
    destination: [''],
    venue: [''],
    departureDate: [''],
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
}
