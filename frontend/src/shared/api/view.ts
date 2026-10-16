import type { ViewSpec, ViewState } from '../types'
import { request } from './client'

export function getViewState(graphId: string): Promise<ViewState> {
  return request<ViewState>(`/graphs/${graphId}/view-state`)
}

export function saveViewState(
  graphId: string,
  spec: ViewSpec,
): Promise<ViewState> {
  return request<ViewState>(`/graphs/${graphId}/view-state`, {
    method: 'PUT',
    body: JSON.stringify({ spec }),
  })
}
