import { ActionIcon, Badge, Button } from '@mantine/core'
import { Trash2 } from 'lucide-react'
import { SelectField, TextField } from '../../../shared/components/form/controls'
import { ENUM_DATATYPE, datatypeOptions, type PropertyDraft } from '../lib/nodeDetails'
import { EnumValuesEditor } from './EnumValuesEditor'

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
    <Button
      type="button"
      variant={active ? 'light' : 'default'}
      size="compact-xs"
      radius="xl"
      title={title}
      aria-pressed={active}
      onClick={onToggle}
    >
      {label}
    </Button>
  )
}

/** 抽屉里的一行字段：字段名、类型、枚举取值、标记开关与删除；必填只是只读标记。 */
export function PropertyFieldRow({
  property,
  required,
  onChange,
  onRemove,
}: PropertyFieldRowProps) {
  return (
    <div className="field-row">
      <div className="field-row__main">
        <div className="field-row__names">
          <TextField
            value={property.name}
            aria-label="字段名"
            placeholder="字段名"
            onChange={(event) => onChange(property.key, { name: event.target.value })}
          />
          <TextField
            value={property.nameEn}
            aria-label="字段英文名"
            placeholder="英文名"
            onChange={(event) => onChange(property.key, { nameEn: event.target.value })}
          />
        </div>
        <SelectField
          value={property.datatype}
          aria-label="字段类型"
          data={datatypeOptions(property.datatype)}
          allowDeselect={false}
          onChange={(datatype) => datatype && onChange(property.key, { datatype })}
        />
        {required ? (
          <Badge size="xs" variant="light" color="yellow">
            必填
          </Badge>
        ) : null}
        <ActionIcon
          type="button"
          variant="subtle"
          color="red"
          size="sm"
          aria-label={`删除字段 ${property.name || '未命名'}`}
          onClick={() => onRemove(property.key)}
        >
          <Trash2 size={15} />
        </ActionIcon>
      </div>
      {property.datatype === ENUM_DATATYPE ? (
        <EnumValuesEditor
          propertyName={property.name}
          values={property.enumValues}
          onChange={(enumValues) => onChange(property.key, { enumValues })}
        />
      ) : null}
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
