export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  details?: string[];
}
