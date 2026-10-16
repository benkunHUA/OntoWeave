import type { Changeset } from '../../../shared/types'

/**
 * 抽屉里能标记删除、且要等确认才真删的元素种类。
 * 这些元素在确认前还留在库里，详情接口照样返回，所以抽屉要按改动集把它们收起来。
 */
const PENDING_DELETE_KINDS = [
  'property',
  'action_parameter',
  'action_rule',
  'action_criteria_node',
] as const

/** 已经标记删除、还没确认的元素 id。 */
export function pendingDeleteIds(changeset: Changeset | null): Set<string> {
  const ids = new Set<string>()
  for (const op of changeset?.ops ?? []) {
    if (op.op !== 'delete' || !op.target_id) continue
    const kind = op.payload.kind
    if (kind && PENDING_DELETE_KINDS.includes(kind as (typeof PENDING_DELETE_KINDS)[number])) {
      ids.add(op.target_id)
    }
  }
  return ids
}
