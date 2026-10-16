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
      <select
        className="action-row__input"
        value={value}
        aria-label={ariaLabel}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">选一个取值</option>
        {(enumValues ?? []).map((item) => (
          <option key={item} value={item}>
            {item}
          </option>
        ))}
      </select>
    )
  }

  return (
    <input
      className="action-row__input"
      value={value}
      aria-label={ariaLabel}
      placeholder={staticValuePlaceholder(datatype, isArray)}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
