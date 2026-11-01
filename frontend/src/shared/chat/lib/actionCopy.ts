/** 行动确认卡片的固定文案与值格式化。 */

export const ACTION_CHANGE_LABELS: Record<string, string> = {
  OBJECT_CREATE: '新增对象',
  OBJECT_MODIFY: '修改对象',
  OBJECT_DELETE: '删除对象',
  RELATION_CREATE: '新增关系',
  RELATION_DELETE: '删除关系',
}

export const ACTION_COPY = {
  alertFallback: '请核对以下变更后再决定是否写入。',
  objectChanges: '对象变更',
  relationChanges: '关系变更',
  warnings: '提示',
  primaryKey: '主键',
  objectType: '对象类型',
  field: '字段',
  before: '变更前',
  after: '变更后',
  emptyValue: '空',
  noFields: '没有字段值变化',
  noRelationChanges: '没有关系变更',
  pendingHint: '确认后才会写入；确认前再次校验数据版本。',
  optionsOnly: '请点击上方按钮确认或取消',
} as const

export function changeLabel(kind: string): string {
  return ACTION_CHANGE_LABELS[kind] ?? '数据变更'
}

export function formatActionValue(value: unknown): string {
  if (value === null || value === undefined) return ACTION_COPY.emptyValue
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.map(formatActionValue).join('、')
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

export function isApproveOption(answer: unknown): boolean {
  if (!answer || typeof answer !== 'object') return false
  return (answer as { decision?: unknown }).decision === 'approve'
}
