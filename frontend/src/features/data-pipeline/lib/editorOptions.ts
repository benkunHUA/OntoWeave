export const DATA_TYPE_OPTIONS = ['string', 'long', 'double', 'boolean', 'date', 'timestamp'] as const

export const CONDITION_KIND_OPTIONS = [
  { value: 'compare', label: '比较' },
  { value: 'and', label: '全部满足' },
  { value: 'or', label: '任一满足' },
] as const

export const CONDITION_OPERATOR_OPTIONS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'in', label: '属于集合' },
  { value: 'is_null', label: '为空' },
  { value: 'is_not_null', label: '不为空' },
] as const

export const COMPARISON_OPERATOR_OPTIONS = CONDITION_OPERATOR_OPTIONS.slice(0, 6)

export const EXPRESSION_KIND_OPTIONS = [
  { value: 'field', label: '字段' },
  { value: 'literal', label: '字面量' },
  { value: 'cast', label: '类型转换' },
  { value: 'case', label: '条件分支' },
  { value: 'compare', label: '比较' },
  { value: 'logical', label: '逻辑组合' },
  { value: 'isNull', label: '空值判断' },
] as const

export const EXPECTATION_TYPE_OPTIONS = [
  { value: 'PRIMARY_KEY_UNIQUE', label: '主键唯一' },
  { value: 'ROW_COUNT_RANGE', label: '行数范围' },
  { value: 'VALUE_SET', label: '取值集合' },
  { value: 'NOT_NULL', label: '非空' },
] as const

export const EDITOR_COPY = {
  addCondition: '添加条件',
  addBranch: '添加分支',
  addExpression: '添加表达式',
  addField: '添加字段',
  addValue: '添加取值',
  remove: '移除',
  field: '字段',
  value: '值',
  dataType: '数据类型',
  conditionKind: '条件类型',
  operator: '运算符',
  expressionKind: '表达式类型',
  thenValue: '结果',
  otherwise: '否则',
  castType: '目标类型',
  left: '左侧',
  right: '右侧',
  fieldPlaceholder: '输入字段名',
  valuePlaceholder: '输入取值',
  primaryKey: '主键字段',
  expectations: '数据期望',
  addExpectation: '添加期望',
  outputName: '输出数据集名称',
  writeMode: '写入模式',
  snapshot: '快照覆盖',
  outputBound: '输出已绑定，配置为只读。',
  saveOutput: '保存输出配置',
  saving: '保存中',
  outputInvalid: '请填写输出名称后再保存',
  columns: '字段',
  column: '字段',
  minRows: '最小行数',
  maxRows: '最大行数',
  values: '允许取值',
} as const
