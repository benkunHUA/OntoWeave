import { Trash2 } from 'lucide-react'
import { datatypeOptions, type PropertyDraft } from '../lib/nodeDetails'

interface PropertyFieldRowProps {
  property: PropertyDraft
  required: boolean
  onChange: (key: string, patch: Partial<PropertyDraft>) => void
  onRemove: (key: string) => void
}

interface FlagToggleProps {
  label: string
  title: string
  active: boolean
  onToggle: () => void
}

/** 字段标记开关：多值 / 主键 / 标题键。 */
function FlagToggle({ label, title, active, onToggle }: FlagToggleProps) {
  return (
    <button
      type="button"
      className={`field-row__flag${active ? ' is-on' : ''}`}
      title={title}
      aria-pressed={active}
      onClick={onToggle}
    >
      {label}
    </button>
  )
}

/** 抽屉里的一行字段：字段名、类型、标记开关与删除；必填只是只读标记。 */
export function PropertyFieldRow({
  property,
  required,
  onChange,
  onRemove,
}: PropertyFieldRowProps) {
  return (
    <div className="field-row">
      <div className="field-row__main">
        <input
          className="field-row__name"
          value={property.name}
          aria-label="字段名"
          placeholder="字段名"
          onChange={(event) => onChange(property.key, { name: event.target.value })}
        />
        <select
          className="field-row__type"
          value={property.datatype}
          aria-label="字段类型"
          onChange={(event) => onChange(property.key, { datatype: event.target.value })}
        >
          {datatypeOptions(property.datatype).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {required ? <span className="field-row__required">必填</span> : null}
        <button
          type="button"
          className="field-row__remove"
          aria-label={`删除字段 ${property.name || '未命名'}`}
          onClick={() => onRemove(property.key)}
        >
          <Trash2 size={15} />
        </button>
      </div>
      <div className="field-row__flags">
        <FlagToggle
          label="多值"
          title="允许多个值（数组）"
          active={property.isArray}
          onToggle={() => onChange(property.key, { isArray: !property.isArray })}
        />
        <FlagToggle
          label="主键"
          title="作为对象的主键（每个对象类型至多一个）"
          active={property.isPrimaryKey}
          onToggle={() => onChange(property.key, { isPrimaryKey: !property.isPrimaryKey })}
        />
        <FlagToggle
          label="标题"
          title="作为对象的标题键（每个对象类型至多一个）"
          active={property.isTitleKey}
          onToggle={() => onChange(property.key, { isTitleKey: !property.isTitleKey })}
        />
      </div>
    </div>
  )
}
