export function safeRedirect(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith('/')) return null
  if (value.startsWith('//')) return null
  if (value === '/auth' || value.startsWith('/auth/') || value.startsWith('/auth?')) return null
  return value
}