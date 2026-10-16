import { Plus, Trash2 } from 'lucide-react'
import { MIN_ENUM_VALUES, hasUsableEnumValues } from '../lib/nodeDetails'

interface EnumValuesEditorProps {
  /** 所属字段名，用于把无障碍标签拼清楚。 */
  propertyName: string
  values: string[]
  onChange: (values: string[]) => void
}

/**
 * 枚举字段的候选值编辑：一行一个取值，可增可删。
 *
 * 草稿里允许暂时不合法（正在输入的中间态），提交前由 `draftValidationError` 拦下来。
 */
export function EnumValuesEditor({ propertyName, values, onChange }: EnumValuesEditorProps) {
  const label = propertyName.trim() || '该字段'

  function updateValue(index: number, value: string) {
    onChange(values.map((current, position) => (position === index ? value : current)))
  }

  function removeValue(index: number) {
    onChange(values.filter((_current, position) => position !== index))
  }

  return (
    <div className="enum-values">
      <span className="enum-values__title">取值</span>
      {values.map((value, index) => (
        <div className="enum-values__row" key={index}>
          <input
            className="enum-values__input"
            value={value}
            aria-label={`${label} 的取值 ${index + 1}`}
            placeholder={`取值 ${index + 1}`}
            onChange={(event) => updateValue(index, event.target.value)}
          />
          <button
            type="button"
            className="field-row__remove"
            aria-label={`删除取值 ${value || index + 1}`}
            onClick={() => removeValue(index)}
          >
            <Trash2 size={15} />
          </button>
        </div>
      ))}
      <div className="enum-values__foot">
        <button type="button" className="text-button" onClick={() => onChange([...values, ''])}>
          <Plus size={14} />
          新增取值
        </button>
        {hasUsableEnumValues(values) ? null : (
          <span className="enum-values__hint">
            至少 {MIN_ENUM_VALUES} 个互不相同的取值
          </span>
        )}
      </div>
    </div>
  )
}
