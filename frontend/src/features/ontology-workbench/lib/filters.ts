import type {
  FilterCondition,
  FilterOp,
  OntologyProperty,
  PropertyDatatype,
} from '../../../shared/types'

export interface FilterOperatorOption {
  value: FilterOp
  label: string
  unary: boolean
}

export interface FilterDraft {
  id: string
  attributeId: string
  op: FilterOp
  value: string
}

export type FilterBuildResult =
  | { valid: true; conditions: FilterCondition[] }
  | { valid: false; reason: string; draftId: string }

export const FILTER_OPERATORS: Record<PropertyDatatype, readonly FilterOperatorOption[]> = {
  text: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
    { value: 'contains', label: '包含', unary: false },
    { value: 'is_null', label: '为空', unary: true },
    { value: 'is_not_null', label: '不为空', unary: true },
  ],
  enum: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
  ],
  integer: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
    { value: 'gt', label: '大于', unary: false },
    { value: 'gte', label: '大于等于', unary: false },
    { value: 'lt', label: '小于', unary: false },
    { value: 'lte', label: '小于等于', unary: false },
    { value: 'is_null', label: '为空', unary: true },
    { value: 'is_not_null', label: '不为空', unary: true },
  ],
  decimal: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
    { value: 'gt', label: '大于', unary: false },
    { value: 'gte', label: '大于等于', unary: false },
    { value: 'lt', label: '小于', unary: false },
    { value: 'lte', label: '小于等于', unary: false },
    { value: 'is_null', label: '为空', unary: true },
    { value: 'is_not_null', label: '不为空', unary: true },
  ],
  date: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
    { value: 'gt', label: '晚于', unary: false },
    { value: 'gte', label: '不早于', unary: false },
    { value: 'lt', label: '早于', unary: false },
    { value: 'lte', label: '不晚于', unary: false },
    { value: 'is_null', label: '为空', unary: true },
    { value: 'is_not_null', label: '不为空', unary: true },
  ],
  timestamp: [
    { value: 'eq', label: '等于', unary: false },
    { value: 'ne', label: '不等于', unary: false },
    { value: 'gt', label: '晚于', unary: false },
    { value: 'gte', label: '不早于', unary: false },
    { value: 'lt', label: '早于', unary: false },
    { value: 'lte', label: '不晚于', unary: false },
    { value: 'is_null', label: '为空', unary: true },
    { value: 'is_not_null', label: '不为空', unary: true },
  ],
  boolean: [{ value: 'eq', label: '等于', unary: false }],
}

export const FILTER_COPY = {
  title: '筛选条件',
  hint: '条件之间为 AND 关系，全部命中才会返回。',
  add: '添加条件',
  remove: '删除条件',
  apply: '应用筛选',
  clear: '清空筛选',
  attribute: '属性',
  operator: '操作符',
  value: '值',
  empty: '暂无筛选条件',
  invalid: '请补全筛选条件',
  selectValue: '请选择',
  true: '是',
  false: '否',
} as const

let draftSequence = 0

export function operatorsFor(datatype: PropertyDatatype): readonly FilterOperatorOption[] {
  return FILTER_OPERATORS[datatype] ?? FILTER_OPERATORS.text
}

export function isUnaryOperator(op: FilterOp): boolean {
  return op === 'is_null' || op === 'is_not_null'
}

export function defaultOperator(property: OntologyProperty): FilterOp {
  return operatorsFor(property.datatype)[0]?.value ?? 'eq'
}

export function createFilterDraft(property: OntologyProperty): FilterDraft {
  draftSequence += 1
  return {
    id: `ontology-filter-${draftSequence}`,
    attributeId: property.id,
    op: defaultOperator(property),
    value: property.datatype === 'boolean' ? 'true' : '',
  }
}

export function filterInputType(datatype: PropertyDatatype): 'text' | 'number' | 'date' | 'datetime-local' | 'select' {
  if (datatype === 'integer' || datatype === 'decimal') return 'number'
  if (datatype === 'date') return 'date'
  if (datatype === 'timestamp') return 'datetime-local'
  if (datatype === 'boolean') return 'select'
  return 'text'
}

function integerValue(raw: string): number | null {
  if (!/^-?\d+$/.test(raw.trim())) return null
  const value = Number(raw)
  return Number.isSafeInteger(value) ? value : null
}

function decimalValue(raw: string): number | null {
  if (!raw.trim()) return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

function timestampValue(raw: string): string | null {
  if (!raw.trim()) return null
  const parsed = Date.parse(raw)
  return Number.isNaN(parsed) ? null : new Date(parsed).toISOString()
}

export function parseFilterValue(
  property: OntologyProperty,
  op: FilterOp,
  raw: string,
): unknown | undefined {
  if (isUnaryOperator(op)) return undefined
  if (!raw.trim()) return undefined
  if (property.datatype === 'text' || property.datatype === 'enum') return raw
  if (property.datatype === 'integer') return integerValue(raw) ?? undefined
  if (property.datatype === 'decimal') return decimalValue(raw) ?? undefined
  if (property.datatype === 'boolean') return raw === 'true' ? true : raw === 'false' ? false : undefined
  if (property.datatype === 'date') {
    return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : undefined
  }
  return timestampValue(raw) ?? undefined
}

export function buildFilterConditions(
  drafts: readonly FilterDraft[],
  properties: readonly OntologyProperty[],
): FilterBuildResult {
  const byId = new Map(properties.map((property) => [property.id, property]))
  const conditions: FilterCondition[] = []
  for (const draft of drafts) {
    const property = byId.get(draft.attributeId)
    if (!property) return { valid: false, reason: FILTER_COPY.invalid, draftId: draft.id }
    if (property.is_array) {
      return { valid: false, reason: '数组属性不支持筛选', draftId: draft.id }
    }
    const operators = operatorsFor(property.datatype)
    if (!operators.some((operator) => operator.value === draft.op)) {
      return { valid: false, reason: '筛选操作符与属性类型不匹配', draftId: draft.id }
    }
    if (isUnaryOperator(draft.op)) {
      conditions.push({ attribute_id: draft.attributeId, op: draft.op })
      continue
    }
    const value = parseFilterValue(property, draft.op, draft.value)
    if (value === undefined) {
      return { valid: false, reason: `“${property.name}”的筛选值不合法`, draftId: draft.id }
    }
    conditions.push({ attribute_id: draft.attributeId, op: draft.op, value })
  }
  return { valid: true, conditions }
}
