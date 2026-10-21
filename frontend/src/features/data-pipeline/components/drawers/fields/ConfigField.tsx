import type { ConfigSchema } from '../../../../../shared/types'
import { WIDGETS } from '../../../lib/constants'
import { ArrayRowsControl, type ArrayRowColumn } from './ArrayRowsControl'
import { DatasetPickerField } from './DatasetPickerField'
import { JsonControl } from './JsonControl'
import { ListControl } from './ListControl'
import { BooleanControl, NumberControl, SelectControl, TextControl } from './scalars'

interface ConfigFieldProps {
  field: string
  widget: string
  schema: ConfigSchema
  value: unknown
  onChange: (value: unknown) => void
}

function enumOf(schema: ConfigSchema | undefined): string[] {
  return Array.isArray(schema?.enum)
    ? schema.enum.filter((item): item is string => typeof item === 'string')
    : []
}

function rowsConfig(
  widget: string,
  schema: ConfigSchema,
): { columns: ArrayRowColumn[]; emptyRow: Record<string, string>; addLabel: string } | null {
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
      emptyRow: { op: '', field: '', outputField: '' },
      addLabel: '聚合项',
    }
  }
  return null
}

export function ConfigField({ field, widget, schema, value, onChange }: ConfigFieldProps) {
  if (field === 'datasetId') {
    return <DatasetPickerField value={value} onChange={onChange} />
  }
  if (field === 'outputId') {
    return (
      <p className="pipeline-readonly">
        {typeof value === 'string' && value ? value : '设置输出后由服务端生成'}
      </p>
    )
  }

  const rows = rowsConfig(widget, schema)
  if (rows) {
    return (
      <ArrayRowsControl
        value={value}
        columns={rows.columns}
        emptyRow={rows.emptyRow}
        addLabel={rows.addLabel}
        onChange={onChange}
      />
    )
  }

  switch (widget) {
    case WIDGETS.number:
      return <NumberControl value={value} schema={schema} onChange={onChange} />
    case WIDGETS.boolean:
      return <BooleanControl value={value} schema={schema} onChange={onChange} />
    case WIDGETS.select:
    case WIDGETS.operatorSelector:
      return <SelectControl value={value} schema={schema} onChange={onChange} />
    case WIDGETS.multiSelect:
    case WIDGETS.fieldSelector:
      return <ListControl value={value} onChange={onChange} />
    case WIDGETS.conditionTree:
      return (
        <JsonControl
          value={value}
          onChange={onChange}
          hint="条件树来自节点目录契约：compare / and / or，value 为带 dataType 的字面量。"
        />
      )
    case WIDGETS.expressionTree:
      return (
        <JsonControl
          value={value}
          onChange={onChange}
          hint="表达式来自节点目录契约：field / literal / cast / case / compare / logical / isNull。"
        />
      )
    default:
      return <TextControl value={value} schema={schema} onChange={onChange} />
  }
}
