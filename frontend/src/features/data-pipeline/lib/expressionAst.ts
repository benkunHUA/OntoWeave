export interface ExpressionNode {
  kind: string
  field?: string
  value?: unknown
  dataType?: string
  expression?: ExpressionNode
  op?: string
  left?: ExpressionNode
  right?: ExpressionNode
  expressions?: ExpressionNode[]
  branches?: Array<{ when: ExpressionNode; then: ExpressionNode }>
  otherwise?: ExpressionNode
}

export function emptyExpression(kind = 'field'): ExpressionNode {
  if (kind === 'literal') return { kind, value: '', dataType: 'string' }
  if (kind === 'cast') return { kind, expression: emptyExpression(), dataType: 'string' }
  if (kind === 'case') {
    return {
      kind,
      branches: [{ when: emptyExpression(), then: emptyExpression() }],
      otherwise: emptyExpression(),
    }
  }
  if (kind === 'compare') {
    return { kind, op: 'eq', left: emptyExpression(), right: { kind: 'literal', value: '', dataType: 'string' } }
  }
  if (kind === 'logical') {
    return { kind, op: 'and', expressions: [emptyExpression(), emptyExpression()] }
  }
  if (kind === 'isNull') return { kind, expression: emptyExpression() }
  return { kind: 'field', field: '' }
}

function objectOf(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

export function normalizeExpression(value: unknown): ExpressionNode {
  const source = objectOf(value)
  const kind = typeof source.kind === 'string' ? source.kind : 'field'
  const base = emptyExpression(kind)
  if (kind === 'field') return { ...base, field: typeof source.field === 'string' ? source.field : '' }
  if (kind === 'literal') {
    return {
      ...base,
      value: source.value ?? '',
      dataType: typeof source.dataType === 'string' ? source.dataType : 'string',
    }
  }
  if (kind === 'cast') {
    return {
      ...base,
      expression: normalizeExpression(source.expression),
      dataType: typeof source.dataType === 'string' ? source.dataType : 'string',
    }
  }
  if (kind === 'case') {
    const branches = Array.isArray(source.branches) ? source.branches.map((branch) => {
      const item = objectOf(branch)
      return { when: normalizeExpression(item.when), then: normalizeExpression(item.then) }
    }) : []
    return {
      ...base,
      branches: branches.length ? branches : [{ when: emptyExpression(), then: emptyExpression() }],
      otherwise: normalizeExpression(source.otherwise),
    }
  }
  if (kind === 'compare') {
    return {
      ...base,
      op: typeof source.op === 'string' ? source.op : 'eq',
      left: normalizeExpression(source.left),
      right: normalizeExpression(source.right),
    }
  }
  if (kind === 'logical') {
    const expressions = Array.isArray(source.expressions)
      ? source.expressions.map(normalizeExpression)
      : []
    return {
      ...base,
      op: source.op === 'or' ? 'or' : 'and',
      expressions: expressions.length >= 2 ? expressions : [emptyExpression(), emptyExpression()],
    }
  }
  if (kind === 'isNull') return { ...base, expression: normalizeExpression(source.expression) }
  return emptyExpression()
}
