import { Component, signal } from '@angular/core';
import { AccountInfoComponent } from './account-info/account-info.component';
import { PreferencesComponent } from './preferences/preferences.component';
import { SavedPassengersComponent } from './saved-passengers/saved-passengers.component';

type AccountTab = 'info' | 'passengers' | 'preferences';

interface TabOption {
  value: AccountTab;
  label: string;
  icon: string;
}

@Component({
  selector: 'tw-account',
  imports: [AccountInfoComponent, SavedPassengersComponent, PreferencesComponent],
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss',
})
export class AccountComponent {
  readonly tabs: TabOption[] = [
    { value: 'info', label: 'Account Info', icon: '👤' },
    { value: 'passengers', label: 'Saved Passengers', icon: '🧳' },
    { value: 'preferences', label: 'Preferences', icon: '⚙️' },
  ];

  readonly activeTab = signal<AccountTab>('info');

  selectTab(tab: AccountTab): void {
    this.activeTab.set(tab);
  }
}
