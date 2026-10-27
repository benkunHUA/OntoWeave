import type { Changeset, GraphSnapshot } from '../../../shared/types'
import { hasPendingOntologyChanges } from './changesetSummary'

export const PUBLISH_COPY = {
  unpublished: '未发布',
  published: '已发布',
  reading: '读取发布状态…',
  unknown: '发布状态未知',
  publish: '发布图',
  republish: '重新发布',
  publishing: '发布中…',
  loadingGraph: '图还在加载',
  noObjects: '至少需要一个对象类型',
  openChangeset: '请先确认当前改动',
  draftElements: (count: number) => `有 ${count} 个草稿元素待确认`,
  loadError: '读取发布状态失败',
  publishError: '发布图失败',
  publishedHint: '已发布到业务问答，重新发布会覆盖当前图卡',
} as const

/** 发布前置条件与后端保持一致：不能有草稿对象、属性或关系。 */
export function publicationBlockedReason(
  snapshot: GraphSnapshot | null,
  changeset: Changeset | null,
): string | null {
  if (!snapshot) return PUBLISH_COPY.loadingGraph
  if (hasPendingOntologyChanges(changeset)) return PUBLISH_COPY.openChangeset
  if (!snapshot.nodes.length) return PUBLISH_COPY.noObjects

  const draftCount =
    snapshot.nodes.filter((node) => node.status === 'draft').length +
    snapshot.nodes
      .flatMap((node) => node.properties)
      .filter((property) => property.status === 'draft').length +
    snapshot.relations.filter((relation) => relation.status === 'draft').length
  return draftCount ? PUBLISH_COPY.draftElements(draftCount) : null
}

export function formatPublishedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
