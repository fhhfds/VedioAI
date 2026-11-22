export interface HealthResponse {
  app: string
  status: string
}

const TIMEOUT_MS = 5000

export async function checkHealth(): Promise<HealthResponse> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), TIMEOUT_MS)

  try {
    const response = await fetch('/api/health', {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    })

    if (!response.ok) {
      throw new Error(`后端返回 HTTP ${response.status}`)
    }

    const payload: unknown = await response.json()
    if (
      typeof payload !== 'object' ||
      payload === null ||
      (payload as Record<string, unknown>).app !== 'vedioai' ||
      (payload as Record<string, unknown>).status !== 'UP'
    ) {
      throw new Error('后端响应格式不符合约定')
    }

    return payload as HealthResponse
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new Error('请求超时（5 秒）')
    }
    if (error instanceof SyntaxError) {
      throw new Error('后端响应不是有效 JSON')
    }
    if (error instanceof TypeError) {
      throw new Error('无法连接后端，请确认 9090 已启动')
    }
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}