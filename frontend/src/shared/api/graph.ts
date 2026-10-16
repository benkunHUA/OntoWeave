import type { GraphSnapshot } from '../types'
import { request } from './client'

export function getGraphSnapshot(graphId: string): Promise<GraphSnapshot> {
  return request<GraphSnapshot>(`/graphs/${graphId}/snapshot`)
}
