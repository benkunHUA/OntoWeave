import { ActionIcon, Button } from '@mantine/core'
import { Trash2 } from 'lucide-react'
import {
  NativeSelectField,
  TextField,
} from '../../../../shared/components/form/controls'
import type { GraphSnapshot } from '../../../../shared/types'
import { PARAMETER_KIND_OPTIONS } from '../../lib/actionConfig'
import type { ActionParameterDraft } from '../../lib/actionDraft'
import { ENUM_DATATYPE, datatypeOptions } from '../../lib/datatypeOptions'
import { objectTypeOptions } from '../../lib/actionOptions'
import { EnumValuesEditor } from '../EnumValuesEditor'
import { ActionStaticValueInput } from './ActionStaticValueInput'

interface ActionParameterRowProps {
  parameter: ActionParameterDraft
  snapshot: GraphSnapshot | null
  issue: string | null
  onChange: (patch: Partial<ActionParameterDraft>) => void
  onRemove: () => void
}

/** 一行参数：名字、种类、种类对应的类型列、默认值与多值 / 必填标记。 */
export function ActionParameterRow({
  parameter,
  snapshot,
  issue,
  onChange,
  onRemove,
}: ActionParameterRowProps) {
  const isValue = parameter.kind === 'value'

  return (
    <div className={`action-row${issue ? ' is-invalid' : ''}`}>
      <div className="action-row__head">
        <TextField
          className="action-row__control"
          value={parameter.name}
          aria-label="参数名"
          placeholder="参数名"
          onChange={(event) => onChange({ name: event.target.value })}
        />
        <NativeSelectField
          className="action-row__control action-row__control--narrow"
          value={parameter.kind}
          aria-label="参数种类"
          data={PARAMETER_KIND_OPTIONS.map((option) => ({
            value: option.value,
            label: option.label,
          }))}
          onChange={(event) => onChange({ kind: event.target.value })}
        />
        <ActionIcon
          type="button"
          variant="subtle"
          color="red"
          size="sm"
          aria-label={`删除参数 ${parameter.name || '未命名'}`}
          onClick={onRemove}
        >
          <Trash2 size={15} />
        </ActionIcon>
      </div>

      <div className="action-row__fields">
        {isValue ? (
          <NativeSelectField
            className="action-row__control"
            value={parameter.datatype}
            aria-label="参数类型"
            data={datatypeOptions(parameter.datatype)}
            onChange={(event) => onChange({ datatype: event.target.value })}
          />
        ) : (
          <NativeSelectField
            className="action-row__control"
            value={parameter.targetObjectTypeId}
            aria-label="参数指向的对象类型"
            data={[
              { value: '', label: '选对象类型' },
              ...objectTypeOptions(snapshot),
            ]}
            onChange={(event) => onChange({ targetObjectTypeId: event.target.value })}
          />
        )}
        <div className="action-row__flags">
          <Button
            type="button"
            variant={parameter.isArray ? 'light' : 'default'}
            size="compact-xs"
            radius="xl"
            aria-pressed={parameter.isArray}
            title="允许多个值（数组）"
            onClick={() => onChange({ isArray: !parameter.isArray })}
          >
            多值
          </Button>
          <Button
            type="button"
            variant={parameter.isRequired ? 'light' : 'default'}
            size="compact-xs"
            radius="xl"
            aria-pressed={parameter.isRequired}
            title="执行动作时必须给这个参数"
            onClick={() => onChange({ isRequired: !parameter.isRequired })}
          >
            必填
          </Button>
        </div>
      </div>

      <TextField
        className="action-row__control action-row__control--wide"
        value={parameter.description}
        aria-label="参数说明"
        placeholder="参数说明：给人和智能体看，写清这个参数指什么"
        onChange={(event) => onChange({ description: event.target.value })}
      />

      {isValue && parameter.datatype === ENUM_DATATYPE ? (
        <EnumValuesEditor
          propertyName={parameter.name}
          values={parameter.enumValues}
          onChange={(enumValues) => onChange({ enumValues })}
        />
      ) : null}

      {isValue ? (
        <div className="action-row__fields">
          <span className="action-row__label">默认值</span>
          <ActionStaticValueInput
            value={parameter.defaultValue}
            onChange={(defaultValue) => onChange({ defaultValue })}
            datatype={parameter.datatype}
            enumValues={parameter.enumValues}
            isArray={parameter.isArray}
            ariaLabel="参数默认值"
          />
        </div>
      ) : null}

      {issue ? <p className="action-row__issue">{issue}</p> : null}
    </div>
  )
}
