export interface UserPreferencesRequest {
  preferredCurrency: string;
  seatPreference: string | null;
  notificationsEnabled: boolean;
}

export interface UserPreferencesResponse {
  userId: number;
  preferredCurrency: string;
  seatPreference: string | null;
  notificationsEnabled: boolean;
  updatedAt: string;
}
