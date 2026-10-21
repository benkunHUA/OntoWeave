export interface LiteralOperand {
  kind: 'literal'
  dataType: string
  value: unknown
}

export interface CompareCondition {
  kind: 'compare'
  field: string
  op: string
  value?: LiteralOperand
  values?: LiteralOperand[]
}

export interface LogicalCondition {
  kind: 'and' | 'or'
  conditions: ConditionNode[]
}

export type ConditionNode = CompareCondition | LogicalCondition

export function literalValue(dataType = 'string', value: unknown = ''): LiteralOperand {
  return { kind: 'literal', dataType, value }
}

export function emptyCompare(): CompareCondition {
  return { kind: 'compare', field: '', op: 'eq', value: literalValue() }
}

function objectOf(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function literalOf(value: unknown): LiteralOperand {
  const source = objectOf(value)
  return {
    kind: 'literal',
    dataType: typeof source.dataType === 'string' ? source.dataType : 'string',
    value: source.value ?? '',
  }
}

export function normalizeCondition(value: unknown): ConditionNode {
  const source = objectOf(value)
  if (source.kind === 'and' || source.kind === 'or') {
    const conditions = Array.isArray(source.conditions)
      ? source.conditions.map(normalizeCondition)
      : []
    return {
      kind: source.kind,
      conditions: conditions.length >= 2 ? conditions : [emptyCompare(), emptyCompare()],
    }
  }
  const op = typeof source.op === 'string' ? source.op : 'eq'
  const normalized: CompareCondition = {
    kind: 'compare',
    field: typeof source.field === 'string' ? source.field : '',
    op,
  }
  if (op === 'in') {
    normalized.values = Array.isArray(source.values) && source.values.length
      ? source.values.map(literalOf)
      : [literalValue()]
  } else if (op !== 'is_null' && op !== 'is_not_null') {
    normalized.value = literalOf(source.value)
  }
  return normalized
}

export function changeConditionKind(node: ConditionNode, kind: string): ConditionNode {
  if (kind === node.kind) return node
  if (kind === 'compare') return emptyCompare()
  return { kind: kind === 'or' ? 'or' : 'and', conditions: [emptyCompare(), emptyCompare()] }
}

export function changeConditionOperator(node: CompareCondition, op: string): CompareCondition {
  const next: CompareCondition = { kind: 'compare', field: node.field, op }
  if (op === 'in') next.values = node.values?.length ? node.values : [literalValue()]
  if (op === 'is_null' || op === 'is_not_null') return next
  if (op !== 'in') next.value = node.value ?? literalValue()
  return next
}
