import type { Changeset, GraphEditOp, GraphNode } from '../../../shared/types'

/** 字段类型下拉的候选项：值用后端约定的英文，界面显示中文。 */
export interface DatatypeOption {
  value: string
  label: string
}

export const DATATYPE_OPTIONS: DatatypeOption[] = [
  { value: 'text', label: '文本' },
  { value: 'integer', label: '整数' },
  { value: 'decimal', label: '小数' },
  { value: 'boolean', label: '布尔' },
  { value: 'date', label: '日期' },
  { value: 'timestamp', label: '日期时间' },
]

const DEFAULT_DATATYPE = 'text'

/** 老数据里可能有非标准类型，兜底把它也放进下拉，别在编辑时悄悄改掉。 */
export function datatypeOptions(current: string): DatatypeOption[] {
  if (DATATYPE_OPTIONS.some((option) => option.value === current)) {
    return DATATYPE_OPTIONS
  }
  return [...DATATYPE_OPTIONS, { value: current, label: current }]
}

export function datatypeLabel(datatype: string): string {
  return DATATYPE_OPTIONS.find((option) => option.value === datatype)?.label ?? datatype
}

export interface PropertyDraft {
  /** React 列表用的稳定 key：已有字段用后端 id，新增字段用临时 id。 */
  key: string
  /** 后端 id；null 表示这次新增、还没保存。 */
  id: string | null
  name: string
  datatype: string
  /** 多值属性（数组）。 */
  isArray: boolean
  /** 每个对象类型至多一个主键 / 标题键。 */
  isPrimaryKey: boolean
  isTitleKey: boolean
}

export interface NodeDraft {
  name: string
  description: string
  properties: PropertyDraft[]
  /** 被删掉的已有字段 id，保存时转成 delete 操作。 */
  removedIds: string[]
}

export function newPropertyDraft(): PropertyDraft {
  return {
    key: crypto.randomUUID(),
    id: null,
    name: '',
    datatype: DEFAULT_DATATYPE,
    isArray: false,
    isPrimaryKey: false,
    isTitleKey: false,
  }
}

/** 已经在改动集里标记删除、但还没确认的字段 id：抽屉里不再把它们当成可编元素。 */
export function pendingPropertyDeleteIds(changeset: Changeset | null): Set<string> {
  const ids = new Set<string>()
  for (const op of changeset?.ops ?? []) {
    if (op.op === 'delete' && op.payload.kind === 'property' && op.target_id) {
      ids.add(op.target_id)
    }
  }
  return ids
}

export function draftFromNode(
  node: GraphNode,
  pendingDeleteIds?: ReadonlySet<string>,
): NodeDraft {
  return {
    name: node.name,
    description: node.description ?? '',
    properties: node.properties
      .filter((property) => !pendingDeleteIds?.has(property.id))
      .map((property) => ({
        key: property.id,
        id: property.id,
        name: property.name,
        datatype: property.datatype,
        isArray: property.is_array,
        isPrimaryKey: property.is_primary_key,
        isTitleKey: property.is_title_key,
      })),
    removedIds: [],
  }
}

function normalizeDescription(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

/**
 * 把抽屉草稿和原节点比对，产出后端要记的编辑操作。
 * 只带真正变了的字段：改动的走 update，新增走 create_property，删掉的走 delete。
 */
export function buildElementEdits(node: GraphNode, draft: NodeDraft): GraphEditOp[] {
  const edits: GraphEditOp[] = []

  const objectPatch: Record<string, string | null> = {}
  const name = draft.name.trim()
  if (name !== node.name) objectPatch.name = name
  const description = normalizeDescription(draft.description)
  if (description !== (node.description ?? null)) objectPatch.description = description
  if (Object.keys(objectPatch).length) {
    edits.push({ op: 'update', element_kind: 'object_type', element_id: node.id, patch: objectPatch })
  }

  const originals = new Map(node.properties.map((property) => [property.id, property]))
  for (const property of draft.properties) {
    const propertyName = property.name.trim()
    if (property.id === null) {
      edits.push({
        op: 'create_property',
        owner_type_id: node.id,
        name: propertyName,
        datatype: property.datatype,
        is_array: property.isArray,
        is_primary_key: property.isPrimaryKey,
        is_title_key: property.isTitleKey,
      })
      continue
    }
    const original = originals.get(property.id)
    if (!original) continue
    const patch: Record<string, string | null | boolean> = {}
    if (propertyName !== original.name) patch.name = propertyName
    if (property.datatype !== original.datatype) patch.datatype = property.datatype
    if (property.isArray !== original.is_array) patch.is_array = property.isArray
    if (property.isPrimaryKey !== original.is_primary_key) {
      patch.is_primary_key = property.isPrimaryKey
    }
    if (property.isTitleKey !== original.is_title_key) patch.is_title_key = property.isTitleKey
    if (Object.keys(patch).length) {
      edits.push({ op: 'update', element_kind: 'property', element_id: property.id, patch })
    }
  }

  for (const id of draft.removedIds) {
    edits.push({ op: 'delete', element_kind: 'property', element_id: id })
  }

  return edits
}

export function isDraftDirty(node: GraphNode, draft: NodeDraft): boolean {
  return buildElementEdits(node, draft).length > 0
}

/** 草稿里有非法输入时返回提示文案，合法返回 null。 */
export function draftValidationError(draft: NodeDraft): string | null {
  if (!draft.name.trim()) return '对象名称不能为空'
  if (draft.properties.some((property) => !property.name.trim())) {
    return '字段名不能为空'
  }
  return null
}
