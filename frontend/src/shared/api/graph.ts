import type { GraphPublication, GraphSnapshot } from '../types'
import { HttpError, request } from './client'

export function getGraphSnapshot(graphId: string): Promise<GraphSnapshot> {
  return request<GraphSnapshot>(`/graphs/${graphId}/snapshot`)
}

export async function getGraphPublication(graphId: string): Promise<GraphPublication | null> {
  try {
    return await request<GraphPublication>(`/graphs/${graphId}/publication`)
  } catch (reason) {
    // 后端用 400 表示“还没有发布过”，这不是页面错误，按未发布处理。
    if (
      reason instanceof HttpError &&
      reason.status === 400 &&
      reason.body.includes('graph has not been published')
    ) {
      return null
    }
    throw reason
  }
}

export function publishGraph(graphId: string): Promise<GraphPublication> {
  return request<GraphPublication>(`/graphs/${graphId}/publish`, { method: 'POST' })
}
