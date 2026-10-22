import { NativeSelectField } from '../../../../shared/components/form/controls'
import type { GraphSnapshot } from '../../../../shared/types'
import { VALUE_SOURCE_OPTIONS } from '../../lib/actionConfig'
import type { ActionOperandDraft, ActionParameterDraft } from '../../lib/actionDraft'
import { valueParameterOptions } from '../../lib/actionOptions'
import { ActionStaticValueInput } from './ActionStaticValueInput'
import { ObjectPropertyPicker } from './ObjectPropertyPicker'

interface ActionOperandFieldsProps {
  operand: ActionOperandDraft
  parameters: ActionParameterDraft[]
  snapshot: GraphSnapshot | null
  /** 只有右侧能放固定值：左侧得自带类型。 */
  allowStatic: boolean
  /** 固定值没有自己的类型，按对面那一侧的类型解析。 */
  staticDatatype: string | null
  staticEnumValues: string[] | null
  sideLabel: string
  onChange: (patch: Partial<ActionOperandDraft>) => void
}

/** 条件的一侧操作数：先选形态，再填对应的那几列。 */
export function ActionOperandFields({
  operand,
  parameters,
  snapshot,
  allowStatic,
  staticDatatype,
  staticEnumValues,
  sideLabel,
  onChange,
}: ActionOperandFieldsProps) {
  const kinds = VALUE_SOURCE_OPTIONS.filter((option) => allowStatic || option.value !== 'static')

  return (
    <div className="action-row__fields">
      <NativeSelectField
        className="action-row__control"
        value={operand.kind}
        aria-label={`${sideLabel}取值形态`}
        data={kinds}
        onChange={(event) => onChange({ kind: event.target.value })}
      />

      {operand.kind === 'parameter' ? (
        <NativeSelectField
          className="action-row__control"
          value={operand.valueParameterId}
          aria-label={`${sideLabel}值参数`}
          data={[
            { value: '', label: '选值参数' },
            ...valueParameterOptions(parameters),
          ]}
          onChange={(event) => onChange({ valueParameterId: event.target.value })}
        />
      ) : null}

      {operand.kind === 'object_property' ? (
        <ObjectPropertyPicker
          parameters={parameters}
          snapshot={snapshot}
          objectParameterId={operand.objectParameterId}
          propertyId={operand.objectPropertyId}
          parameterLabel={`${sideLabel}对象参数`}
          propertyLabel={`${sideLabel}属性`}
          onChange={(patch) => onChange(patch)}
        />
      ) : null}

      {operand.kind === 'static' ? (
        <ActionStaticValueInput
          value={operand.staticValue}
          onChange={(staticValue) => onChange({ staticValue })}
          datatype={staticDatatype}
          enumValues={staticEnumValues}
          isArray={false}
          ariaLabel={`${sideLabel}固定值`}
        />
      ) : null}
    </div>
  )
}
