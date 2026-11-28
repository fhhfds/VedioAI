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

export type Role = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface AuthPolicy {
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

export interface ChallengeReceipt {
  challengeId: string
  expiresIn: number
  resendAfter: number
}

export interface AdminUser {
  userId: string
  emailLabel: string
  nickname: string
  status: UserStatus
  roles: Role[]
  version: number
  createdAt: string
}

export type AccountUser = Omit<AdminUser, 'emailLabel'> & {
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

export interface SessionItem {
  sessionId: string
  current: boolean
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  userAgent: string
  ipLabel: string
}

export interface AdminUserPage {
  items: AdminUser[]
  page: number
  pageSize: number
  total: number
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
  adminRoles: { roles: Role[]; expectedVersion: number }
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
  adminUsers: AdminUserPage
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

interface AuthEnvelope<T> {
  data: T
  traceId: string
}

export class AuthApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, unknown> | null

  constructor(status: number, code: string, message: string, details: Record<string, unknown> | null = null) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers || {})
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')

  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      credentials: 'same-origin',
      headers,
    })
  } catch {
    throw new AuthApiError(0, 'NETWORK_ERROR', '无法连接后端服务，请确认后端已启动')
  }

  const text = await response.text()
  let payload: unknown = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    // The server may return an empty body for 204 responses.
  }

  if (!response.ok) {
    const error = (payload as AuthErrorResponse | null)?.error
    throw new AuthApiError(
      response.status,
      error?.code || 'REQUEST_FAILED',
      error?.message || `请求失败（HTTP ${response.status}）`,
      error?.details || null,
    )
  }

  return (payload as AuthEnvelope<T>)?.data
}

export function fetchAuthPolicy(): Promise<AuthPolicy> {
  return apiRequest<AuthPolicy>('/api/auth/policy')
}

export function fetchCsrf(): Promise<{ headerName: string; token: string }> {
  return apiRequest<{ headerName: string; token: string }>('/api/auth/csrf')
}

export async function requestRegistrationChallenge(email: string): Promise<ChallengeReceipt> {
  const csrf = await fetchCsrf()
  return apiRequest<ChallengeReceipt>('/api/auth/registration-challenges', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ email }),
  })
}

export async function registerAccount(input: RequestBodies['register']): Promise<AccountUser> {
  const csrf = await fetchCsrf()
  return apiRequest<AccountUser>('/api/auth/registrations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(input),
  })
}

export async function loginAccount(input: RequestBodies['login']): Promise<AuthState> {
  const csrf = await fetchCsrf()
  return apiRequest<AuthState>('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(input),
  })
}

export function fetchMe(): Promise<AuthState> {
  return apiRequest<AuthState>('/api/auth/me')
}

export async function logoutAccount(): Promise<void> {
  const csrf = await fetchCsrf()
  await apiRequest<void>('/api/auth/logout', {
    method: 'POST',
    headers: {
      [csrf.headerName]: csrf.token,
    },
  })
}

async function csrfHeaders(): Promise<Record<string, string>> {
  const csrf = await fetchCsrf()
  return { [csrf.headerName]: csrf.token }
}

export async function updateProfile(input: RequestBodies['profile']): Promise<AccountUser> {
  return apiRequest<AccountUser>('/api/auth/profile', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify(input),
  })
}

export async function reauthenticate(password: string): Promise<{ validUntil: string }> {
  return apiRequest<{ validUntil: string }>('/api/auth/reauthentication', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify({ password }),
  })
}

export async function fetchSessions(): Promise<{ items: SessionItem[] }> {
  return apiRequest<{ items: SessionItem[] }>('/api/auth/sessions')
}

export async function revokeSession(sessionId: string): Promise<void> {
  await apiRequest<void>(`/api/auth/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
    headers: await csrfHeaders(),
  })
}

export async function revokeAllSessions(): Promise<void> {
  await apiRequest<void>('/api/auth/sessions/revoke-all', {
    method: 'POST',
    headers: await csrfHeaders(),
  })
}

export async function changePassword(input: RequestBodies['changePassword']): Promise<void> {
  await apiRequest<void>('/api/auth/password/change', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify(input),
  })
}

export async function requestPasswordResetChallenge(email: string): Promise<ChallengeReceipt> {
  return apiRequest<ChallengeReceipt>('/api/auth/password-reset-challenges', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify({ email }),
  })
}

export async function resetPassword(input: RequestBodies['resetPassword']): Promise<void> {
  await apiRequest<void>('/api/auth/password/resets', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify(input),
  })
}

export async function fetchAdminUsers(
  page: number,
  pageSize: number,
  status?: UserStatus,
): Promise<AdminUserPage> {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status) query.set('status', status)
  return apiRequest<AdminUserPage>(`/api/admin/users?${query.toString()}`)
}

export async function changeAdminUserStatus(
  userId: string,
  input: RequestBodies['adminStatus'],
): Promise<AdminUser> {
  return apiRequest<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify(input),
  })
}

export async function changeAdminUserRoles(
  userId: string,
  input: RequestBodies['adminRoles'],
): Promise<AdminUser> {
  return apiRequest<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}/roles`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...(await csrfHeaders()),
    },
    body: JSON.stringify(input),
  })
}
