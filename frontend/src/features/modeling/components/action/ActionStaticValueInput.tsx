import { NativeSelectField, TextField } from '../../../../shared/components/form/controls'
import { ENUM_DATATYPE } from '../../lib/datatypeOptions'
import { staticValuePlaceholder } from '../../lib/actionConfig'

interface ActionStaticValueInputProps {
  value: string
  onChange: (value: string) => void
  /** 固定值没有自己的类型，按对面那一侧或目标属性的类型填。 */
  datatype: string | null
  enumValues: string[] | null
  isArray: boolean
  ariaLabel: string
}

/** 固定值输入：枚举给下拉（取值必须来自候选值），其余类型给文本输入。 */
export function ActionStaticValueInput({
  value,
  onChange,
  datatype,
  enumValues,
  isArray,
  ariaLabel,
}: ActionStaticValueInputProps) {
  if (datatype === ENUM_DATATYPE) {
    return (
      <NativeSelectField
        className="action-row__control"
        value={value}
        aria-label={ariaLabel}
        data={[
          { value: '', label: '选一个取值' },
          ...(enumValues ?? []).map((item) => ({ value: item, label: item })),
        ]}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  return (
    <TextField
      className="action-row__control"
      value={value}
      aria-label={ariaLabel}
      placeholder={staticValuePlaceholder(datatype, isArray)}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
