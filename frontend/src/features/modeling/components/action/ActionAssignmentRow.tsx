import { Trash2 } from 'lucide-react'
import type { GraphSnapshot } from '../../../../shared/types'
import { VALUE_SOURCE_OPTIONS } from '../../lib/actionConfig'
import type { ActionAssignmentDraft, ActionParameterDraft } from '../../lib/actionDraft'
import { propertiesOf, propertyOf, valueParameterOptions } from '../../lib/actionOptions'
import { ActionStaticValueInput } from './ActionStaticValueInput'
import { ObjectPropertyPicker } from './ObjectPropertyPicker'

interface ActionAssignmentRowProps {
  assignment: ActionAssignmentDraft
  /** 规则作用的对象类型：能赋值的属性从它身上选。 */
  targetObjectTypeId: string
  parameters: ActionParameterDraft[]
  snapshot: GraphSnapshot | null
  issue: string | null
  onChange: (patch: Partial<ActionAssignmentDraft>) => void
  onRemove: () => void
}

/** 一条字段赋值：目标属性 + 值来源 + 来源对应的那几列。 */
export function ActionAssignmentRow({
  assignment,
  targetObjectTypeId,
  parameters,
  snapshot,
  issue,
  onChange,
  onRemove,
}: ActionAssignmentRowProps) {
  const target = propertyOf(snapshot, assignment.targetPropertyId)?.property ?? null

  return (
    <div className={`action-row${issue ? ' is-invalid' : ''}`}>
      <div className="action-row__head">
        <select
          className="action-row__input"
          value={assignment.targetPropertyId}
          aria-label="赋值的目标属性"
          onChange={(event) => onChange({ targetPropertyId: event.target.value })}
        >
          <option value="">选属性</option>
          {propertiesOf(snapshot, targetObjectTypeId).map((property) => (
            <option key={property.id} value={property.id}>
              {property.name}
            </option>
          ))}
        </select>
        <select
          className="action-row__input action-row__input--narrow"
          value={assignment.valueSource}
          aria-label="值的来源"
          onChange={(event) => onChange({ valueSource: event.target.value })}
        >
          {VALUE_SOURCE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="field-row__remove"
          aria-label="删除这条赋值"
          onClick={onRemove}
        >
          <Trash2 size={15} />
        </button>
      </div>

      <div className="action-row__fields">
        {assignment.valueSource === 'parameter' ? (
          <select
            className="action-row__input"
            value={assignment.valueParameterId}
            aria-label="值的参数"
            onChange={(event) => onChange({ valueParameterId: event.target.value })}
          >
            <option value="">选值参数</option>
            {valueParameterOptions(parameters).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        ) : null}

        {assignment.valueSource === 'object_property' ? (
          <ObjectPropertyPicker
            parameters={parameters}
            snapshot={snapshot}
            objectParameterId={assignment.objectParameterId}
            propertyId={assignment.objectPropertyId}
            parameterLabel="来源对象参数"
            propertyLabel="来源属性"
            onChange={(patch) => onChange(patch)}
          />
        ) : null}

        {assignment.valueSource === 'static' ? (
          <ActionStaticValueInput
            value={assignment.staticValue}
            onChange={(staticValue) => onChange({ staticValue })}
            datatype={target?.datatype ?? null}
            enumValues={target?.enum_values ?? null}
            isArray={target?.is_array ?? false}
            ariaLabel="固定值"
          />
        ) : null}
      </div>

      {issue ? <p className="action-row__issue">{issue}</p> : null}
    </div>
  )
}
