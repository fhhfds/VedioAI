export const AUTH_CSRF_HEADER = 'X-CSRF-TOKEN'

export const authEndpoints = {
  policy: { method: 'GET', path: '/api/auth/policy', success: 200 },
  csrf: { method: 'GET', path: '/api/auth/csrf', success: 200 },
  registrationChallenge: { method: 'POST', path: '/api/auth/registration-challenges', success: 202 },
  register: { method: 'POST', path: '/api/auth/registrations', success: 201 },
  login: { method: 'POST', path: '/api/auth/login', success: 200 },
  logout: { method: 'POST', path: '/api/auth/logout', success: 204 },
  me: { method: 'GET', path: '/api/auth/me', success: 200 },
  changePassword: { method: 'POST', path: '/api/auth/password/change', success: 204 },
  resetChallenge: { method: 'POST', path: '/api/auth/password-reset-challenges', success: 202 },
  resetPassword: { method: 'POST', path: '/api/auth/password/resets', success: 204 },
  sessions: { method: 'GET', path: '/api/auth/sessions', success: 200 },
  revokeSession: { method: 'DELETE', path: '/api/auth/sessions/{sessionId}', success: 204 },
  revokeAll: { method: 'POST', path: '/api/auth/sessions/revoke-all', success: 204 },
  profile: { method: 'PATCH', path: '/api/auth/profile', success: 200 },
  reauthenticate: { method: 'POST', path: '/api/auth/reauthentication', success: 200 },
  adminUsers: { method: 'GET', path: '/api/admin/users', success: 200 },
  adminStatus: { method: 'PATCH', path: '/api/admin/users/{userId}/status', success: 200 },
  adminRoles: { method: 'PUT', path: '/api/admin/users/{userId}/roles', success: 200 },
} as const

type Role = 'USER' | 'ADMIN'
type UserStatus = 'ACTIVE' | 'DISABLED'

interface AuthPolicy {
  registrationEnabled: boolean
  passwordMinCodePoints: number
  passwordMaxCodePoints: number
  otpLength: number
  otpTtlSeconds: number
  resendAfterSeconds: number
  idleTimeoutSeconds: number
  absoluteTimeoutSeconds: number
  maxSessions: number
}

interface ChallengeReceipt {
  challengeId: string
  expiresIn: number
  resendAfter: number
}

interface AdminUser {
  userId: string
  emailLabel: string
  nickname: string
  status: UserStatus
  roles: Role[]
  version: number
  createdAt: string
}

type AccountUser = Omit<AdminUser, 'emailLabel'> & {
  email: string
  emailVerified: boolean
  permissions: string[]
}

export interface AuthState {
  user: AccountUser
  session: {
    sessionId: string
    authenticatedAt: string
    absoluteExpiresAt: string
  }
}

interface SessionItem {
  sessionId: string
  current: boolean
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  userAgent: string
  ipLabel: string
}

interface RequestBodies {
  registrationChallenge: { email: string }
  register: { challengeId: string; code: string; nickname: string; password: string }
  login: { email: string; password: string }
  changePassword: { currentPassword: string; newPassword: string }
  resetChallenge: { email: string }
  resetPassword: { challengeId: string; code: string; newPassword: string }
  profile: { nickname: string; expectedVersion: number }
  reauthenticate: { password: string }
  adminStatus: { status: UserStatus; expectedVersion: number }
  adminRoles: { roles: ['USER'] | ['USER', 'ADMIN'] | ['ADMIN', 'USER']; expectedVersion: number }
}

interface ResponseData {
  policy: AuthPolicy
  csrf: { headerName: typeof AUTH_CSRF_HEADER; token: string }
  registrationChallenge: ChallengeReceipt
  register: AccountUser
  login: AuthState
  me: AuthState
  resetChallenge: ChallengeReceipt
  sessions: { items: SessionItem[] }
  profile: AccountUser
  reauthenticate: { validUntil: string }
  adminUsers: { items: AdminUser[]; page: number; pageSize: number; total: number }
  adminStatus: AdminUser
  adminRoles: AdminUser
}

export type AuthRequest<Operation extends keyof RequestBodies> = RequestBodies[Operation]

export type AuthResponse<Operation extends keyof typeof authEndpoints> =
  Operation extends keyof ResponseData ? { data: ResponseData[Operation]; traceId: string } : void

export interface AuthErrorResponse {
  error: {
    code: string
    message: string
    details: Record<string, unknown> | null
  }
  traceId: string
}
