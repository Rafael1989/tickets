export type UserRole = 'CUSTOMER' | 'OPERATOR' | 'SUPPORT' | 'ADMIN';

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  createdAt: string;
}

export interface UserRequest {
  username: string;
  password: string;
  email: string;
  role: UserRole;
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
