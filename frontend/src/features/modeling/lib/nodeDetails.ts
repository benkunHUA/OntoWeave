import type { EditPatchValue, GraphEditOp, GraphNode } from '../../../shared/types'
import {
  ENUM_DATATYPE,
  MIN_ENUM_VALUES,
  enumValuesFor,
  hasUsableEnumValues,
  sameEnumValues,
} from './datatypeOptions'

// 字段类型是对象与动作参数共用的词表，实现在 `datatypeOptions`；这里原样再导出，
// 已有调用方（字段行、枚举编辑器）不用改 import。
export {
  DATATYPE_OPTIONS,
  ENUM_DATATYPE,
  MIN_ENUM_VALUES,
  datatypeLabel,
  datatypeOptions,
  hasUsableEnumValues,
  type DatatypeOption,
} from './datatypeOptions'

const DEFAULT_DATATYPE = 'text'

export interface PropertyDraft {
  /** React 列表用的稳定 key：已有字段用后端 id，新增字段用临时 id。 */
  key: string
  /** 后端 id；null 表示这次新增、还没保存。 */
  id: string | null
  name: string
  /** 英文标识；为空表示还没生成或用户主动清空。 */
  nameEn: string
  datatype: string
  /** 多值属性（数组）。 */
  isArray: boolean
  /** 每个对象类型至多一个主键 / 标题键。 */
  isPrimaryKey: boolean
  isTitleKey: boolean
  /** datatype = enum 时的候选值；换成别的类型时留着不动，保存时不发出去。 */
  enumValues: string[]
}

export interface NodeDraft {
  name: string
  nameEn: string
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
    nameEn: '',
    datatype: DEFAULT_DATATYPE,
    isArray: false,
    isPrimaryKey: false,
    isTitleKey: false,
    enumValues: [],
  }
}

export function draftFromNode(
  node: GraphNode,
  pendingDeleteIds?: ReadonlySet<string>,
): NodeDraft {
  return {
    name: node.name,
    nameEn: node.name_en ?? '',
    description: node.description ?? '',
    properties: node.properties
      .filter((property) => !pendingDeleteIds?.has(property.id))
      .map((property) => ({
        key: property.id,
        id: property.id,
        name: property.name,
        nameEn: property.name_en ?? '',
        datatype: property.datatype,
        isArray: property.is_array,
        isPrimaryKey: property.is_primary_key,
        isTitleKey: property.is_title_key,
        enumValues: property.enum_values ?? [],
      })),
    removedIds: [],
  }
}

function normalizeDescription(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function normalizeNameEn(value: string): string | null {
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
  const nameEn = normalizeNameEn(draft.nameEn)
  if (nameEn !== (node.name_en ?? null)) objectPatch.name_en = nameEn
  const description = normalizeDescription(draft.description)
  if (description !== (node.description ?? null)) objectPatch.description = description
  if (Object.keys(objectPatch).length) {
    edits.push({ op: 'update', element_kind: 'object_type', element_id: node.id, patch: objectPatch })
  }

  const originals = new Map(node.properties.map((property) => [property.id, property]))
  for (const property of draft.properties) {
    const propertyName = property.name.trim()
    if (property.id === null) {
      const enumValues = enumValuesFor(property.datatype, property.enumValues)
      const nameEn = normalizeNameEn(property.nameEn)
      edits.push({
        op: 'create_property',
        owner_type_id: node.id,
        name: propertyName,
        ...(nameEn ? { name_en: nameEn } : {}),
        datatype: property.datatype,
        is_array: property.isArray,
        is_primary_key: property.isPrimaryKey,
        is_title_key: property.isTitleKey,
        // 只有枚举才带候选值，别往别的类型上塞一个用不上的空字段。
        ...(enumValues ? { enum_values: enumValues } : {}),
      })
      continue
    }
    const original = originals.get(property.id)
    if (!original) continue
    const patch: Record<string, EditPatchValue> = {}
    if (propertyName !== original.name) patch.name = propertyName
    const nameEn = normalizeNameEn(property.nameEn)
    if (nameEn !== (original.name_en ?? null)) patch.name_en = nameEn
    if (property.datatype !== original.datatype) patch.datatype = property.datatype
    if (property.isArray !== original.is_array) patch.is_array = property.isArray
    if (property.isPrimaryKey !== original.is_primary_key) {
      patch.is_primary_key = property.isPrimaryKey
    }
    if (property.isTitleKey !== original.is_title_key) patch.is_title_key = property.isTitleKey
    const nextEnumValues = enumValuesFor(property.datatype, property.enumValues)
    if (!sameEnumValues(nextEnumValues, original.enum_values ?? null)) {
      patch.enum_values = nextEnumValues
    }
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
  const brokenEnum = draft.properties.find(
    (property) => property.datatype === ENUM_DATATYPE && !hasUsableEnumValues(property.enumValues),
  )
  if (brokenEnum) {
    return `枚举字段「${brokenEnum.name.trim()}」至少要有 ${MIN_ENUM_VALUES} 个互不相同的取值`
  }
  return null
}
