import type { ConfigSchema } from '../../../../../shared/types'
import { WIDGETS } from '../../../lib/constants'
import type { ArrayRowColumn } from './ArrayRowsControl'

interface RowsConfig {
  columns: ArrayRowColumn[]
  emptyRow: Record<string, string>
  addLabel: string
}

function enumOf(schema: ConfigSchema | undefined): string[] {
  return Array.isArray(schema?.enum)
    ? schema.enum.filter((item): item is string => typeof item === 'string')
    : []
}

export function rowsConfig(widget: string, schema: ConfigSchema): RowsConfig | null {
  if (widget === WIDGETS.renameList) {
    return {
      columns: [{ key: 'from', label: '原字段' }, { key: 'to', label: '新字段' }],
      emptyRow: { from: '', to: '' },
      addLabel: '重命名',
    }
  }
  if (widget === WIDGETS.joinKeys) {
    return {
      columns: [{ key: 'left', label: '左表字段' }, { key: 'right', label: '右表字段' }],
      emptyRow: { left: '', right: '' },
      addLabel: '连接键',
    }
  }
  if (widget === WIDGETS.aggregationList) {
    const itemProperties = schema.items?.properties ?? {}
    return {
      columns: [
        { key: 'op', label: '聚合', options: enumOf(itemProperties.op) },
        { key: 'field', label: '字段' },
        { key: 'outputField', label: '输出字段' },
      ],
      emptyRow: { op: enumOf(itemProperties.op)[0] ?? '', field: '', outputField: '' },
      addLabel: '聚合项',
    }
  }
  return null
}
