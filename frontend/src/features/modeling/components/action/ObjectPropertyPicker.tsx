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
      <select
        className="action-row__input"
        value={objectParameterId}
        aria-label={parameterLabel}
        onChange={(event) =>
          onChange({ objectParameterId: event.target.value, objectPropertyId: '' })
        }
      >
        <option value="">选对象参数</option>
        {objectParameterOptions(parameters, snapshot).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <select
        className="action-row__input"
        value={propertyId}
        aria-label={propertyLabel}
        disabled={!selected}
        onChange={(event) => onChange({ objectPropertyId: event.target.value })}
      >
        <option value="">选属性</option>
        {properties.map((property) => (
          <option key={property.id} value={property.id}>
            {property.name}
          </option>
        ))}
      </select>
    </>
  )
}
