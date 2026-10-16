/** 后端请求内核：拼地址、带 JSON 头、把非 2xx 转成异常。各域接口见同目录其它文件。 */
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'content-type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

/** SSE / 流式响应用的地址。 */
export function apiUrl(path: string) {
  return `${API_BASE}${path}`
}
