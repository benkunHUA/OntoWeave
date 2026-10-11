import type { Changeset } from '../../../shared/types'

/** 需要用户确认的本体操作。视图改动即时生效，不进确认条。 */
const ONTOLOGY_OPS = ['create', 'update', 'delete'] as const

const OP_LABELS: Record<(typeof ONTOLOGY_OPS)[number], string> = {
  create: '新增',
  update: '修改',
  delete: '删除',
}

function countOps(changeset: Changeset | null): Record<string, number> {
  const counts: Record<string, number> = {}
  for (const op of changeset?.ops ?? []) {
    counts[op.op] = (counts[op.op] ?? 0) + 1
  }
  return counts
}

/** 「新增 2 · 修改 1」式的改动摘要；只数本体改动，没有就是空数组。 */
export function formatOpSummary(changeset: Changeset | null): string[] {
  const counts = countOps(changeset)
  return ONTOLOGY_OPS.filter((op) => counts[op]).map((op) => `${OP_LABELS[op]} ${counts[op]}`)
}

/**
 * 该不该弹确认条：只有「待确认的本体改动」才需要用户拍板。
 * 纯视图改动、以及什么都没改的空改动集都不该出现——图还是空的却让人确认，
 * 只会让人以为哪里坏了。
 */
export function hasPendingOntologyChanges(changeset: Changeset | null): boolean {
  if (changeset?.status !== 'open') return false
  return formatOpSummary(changeset).length > 0
}
