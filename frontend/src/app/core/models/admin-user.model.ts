export type UserRole = 'CUSTOMER' | 'OPERATOR' | 'SUPPORT' | 'ADMIN';

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  partnerId: number | null;
  createdAt: string;
}

export interface UserRequest {
  username: string;
  password: string;
  email: string;
  role: UserRole;
  /** Only meaningful (and accepted by the backend) when role is OPERATOR. */
  partnerId?: number | null;
}

export interface RoleUpdateRequest {
  role: UserRole;
}

export interface UpdateEmailRequest {
  email: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
