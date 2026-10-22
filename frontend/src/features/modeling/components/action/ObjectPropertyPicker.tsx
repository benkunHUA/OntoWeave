import { NativeSelectField } from '../../../../shared/components/form/controls'
import type { GraphSnapshot } from '../../../../shared/types'
import type { ActionParameterDraft } from '../../lib/actionDraft'
import { objectParameterOptions, propertiesOf } from '../../lib/actionOptions'

interface ObjectPropertyPickerProps {
  parameters: ActionParameterDraft[]
  snapshot: GraphSnapshot | null
  objectParameterId: string
  propertyId: string
  onChange: (patch: { objectParameterId?: string; objectPropertyId?: string }) => void
  parameterLabel: string
  propertyLabel: string
}

/** 「对象参数的某个属性」这两个下拉：赋值与校验的两侧都用它。 */
export function ObjectPropertyPicker({
  parameters,
  snapshot,
  objectParameterId,
  propertyId,
  onChange,
  parameterLabel,
  propertyLabel,
}: ObjectPropertyPickerProps) {
  const selected = parameters.find((parameter) => parameter.id === objectParameterId)
  const properties = selected ? propertiesOf(snapshot, selected.targetObjectTypeId) : []

  return (
    <>
      <NativeSelectField
        className="action-row__control"
        value={objectParameterId}
        aria-label={parameterLabel}
        data={[
          { value: '', label: '选对象参数' },
          ...objectParameterOptions(parameters, snapshot),
        ]}
        onChange={(event) =>
          onChange({ objectParameterId: event.target.value, objectPropertyId: '' })
        }
      />
      <NativeSelectField
        className="action-row__control"
        value={propertyId}
        aria-label={propertyLabel}
        data={[
          { value: '', label: '选属性' },
          ...properties.map((property) => ({ value: property.id, label: property.name })),
        ]}
        disabled={!selected}
        onChange={(event) => onChange({ objectPropertyId: event.target.value })}
      />
    </>
  )
}
