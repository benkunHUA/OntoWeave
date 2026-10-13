import type { GraphEditOp, GraphRelation } from '../../../shared/types'

/** 关系基数：值用后端约定的英文，界面显示中文与 1:N 记号。 */
export interface CardinalityOption {
  value: string
  label: string
  mark: string
}

export const CARDINALITY_OPTIONS: CardinalityOption[] = [
  { value: 'one_to_one', label: '一对一', mark: '1:1' },
  { value: 'one_to_many', label: '一对多', mark: '1:N' },
  { value: 'many_to_many', label: '多对多', mark: 'N:M' },
]

export function cardinalityLabel(value: string): string {
  return CARDINALITY_OPTIONS.find((option) => option.value === value)?.label ?? value
}

export function cardinalityMark(value: string): string {
  return CARDINALITY_OPTIONS.find((option) => option.value === value)?.mark ?? value
}

/** 只有一对一 / 一对多才用外键属性承载链接；多对多走链接表。 */
export function foreignKeyAllowed(cardinality: string): boolean {
  return cardinality !== 'many_to_many'
}

/** 外键属性下拉里的一个候选：来自关系某一端的某个属性。 */
export interface ForeignKeyCandidate {
  value: string
  label: string
}

/** 边上显示的文案：优先用源端→目标端的可读名，缺省回落到关系名。 */
export function relationEdgeLabel(
  relation: Pick<GraphRelation, 'name' | 'forward_label'>,
): string {
  return relation.forward_label?.trim() || relation.name
}

export interface RelationDraft {
  name: string
  cardinality: string
  /** 源端→目标端的可读名；空串表示未填，保存时按 null 提交。 */
  forwardLabel: string
  reverseLabel: string
  /** 外键属性 id；空串表示未绑定，保存时按 null 提交。 */
  foreignKeyPropertyId: string
}

export function draftFromRelation(relation: GraphRelation): RelationDraft {
  return {
    name: relation.name,
    cardinality: relation.cardinality,
    forwardLabel: relation.forward_label ?? '',
    reverseLabel: relation.reverse_label ?? '',
    foreignKeyPropertyId: relation.foreign_key_property_id ?? '',
  }
}

function normalizeLabel(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

/** 把关系草稿和原关系比对，只产出真正变了的字段。 */
export function buildRelationEdits(
  relation: GraphRelation,
  draft: RelationDraft,
): GraphEditOp[] {
  const patch: Record<string, string | null | boolean> = {}
  const name = draft.name.trim()
  if (name !== relation.name) patch.name = name
  if (draft.cardinality !== relation.cardinality) patch.cardinality = draft.cardinality
  const forward = normalizeLabel(draft.forwardLabel)
  if (forward !== (relation.forward_label ?? null)) patch.forward_label = forward
  const reverse = normalizeLabel(draft.reverseLabel)
  if (reverse !== (relation.reverse_label ?? null)) patch.reverse_label = reverse
  const foreignKey = draft.foreignKeyPropertyId || null
  if (foreignKey !== (relation.foreign_key_property_id ?? null)) {
    patch.foreign_key_property_id = foreignKey
  }

  return Object.keys(patch).length
    ? [{ op: 'update', element_kind: 'relation', element_id: relation.id, patch }]
    : []
}

export function isRelationDraftDirty(relation: GraphRelation, draft: RelationDraft): boolean {
  return buildRelationEdits(relation, draft).length > 0
}

export function relationDraftValidationError(draft: RelationDraft): string | null {
  if (!draft.name.trim()) return '关系名不能为空'
  if (!foreignKeyAllowed(draft.cardinality) && draft.foreignKeyPropertyId) {
    return '多对多关系不用外键属性，请改基数或清空外键'
  }
  return null
}

/** 删除关系：草稿阶段只标记，确认时才真删（幽灵态）。 */
export function deleteRelationEdit(relationId: string): GraphEditOp {
  return { op: 'delete', element_kind: 'relation', element_id: relationId }
}
