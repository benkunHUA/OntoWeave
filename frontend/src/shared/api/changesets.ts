import type { Changeset, GraphEditOp } from '../types'
import { request } from './client'

export function listChangesets(graphId: string): Promise<Changeset[]> {
  return request<Changeset[]>(`/graphs/${graphId}/changesets?limit=50`)
}

export function getChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}`)
}

export function confirmChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}/confirm`, {
    method: 'POST',
  })
}

export function rejectChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}/reject`, {
    method: 'POST',
  })
}

/** 抽屉里的表单保存：把对象/字段的编辑登记进图的改动集，返回改动集详情。 */
export function editGraphElements(
  graphId: string,
  edits: GraphEditOp[],
): Promise<Changeset> {
  return request<Changeset>(`/graphs/${graphId}/edits`, {
    method: 'POST',
    body: JSON.stringify({ edits }),
  })
}
