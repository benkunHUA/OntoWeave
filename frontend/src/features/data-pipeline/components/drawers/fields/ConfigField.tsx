import type { ConfigSchema } from '../../../../../shared/types'
import { WIDGETS } from '../../../lib/constants'
import { ArrayRowsControl } from './ArrayRowsControl'
import { ConditionTreeEditor } from './ConditionTreeEditor'
import { DatasetPickerField } from './DatasetPickerField'
import { ExpressionEditor } from './ExpressionEditor'
import { FieldMultiSelect } from './FieldMultiSelect'
import { GroupByPicker } from './GroupByPicker'
import { rowsConfig } from './rowsConfig'
import { BooleanControl, NumberControl, SelectControl, TextControl } from './scalars'

interface ConfigFieldProps {
  field: string
  widget: string
  schema: ConfigSchema
  value: unknown
  onChange: (value: unknown) => void
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
      return <FieldMultiSelect value={value} onChange={onChange} />
    case WIDGETS.fieldSelector:
      return field === 'groupBy'
        ? <GroupByPicker value={value} onChange={onChange} />
        : <FieldMultiSelect value={value} onChange={onChange} />
    case WIDGETS.conditionTree:
      return <ConditionTreeEditor value={value} onChange={onChange} />
    case WIDGETS.expressionTree:
      return <ExpressionEditor value={value} onChange={onChange} />
    default:
      return <TextControl value={value} schema={schema} onChange={onChange} />
  }
}
