import type { GraphSnapshot } from '../../../../shared/types'
import { ACTION_COPY, ACTION_SECTION_COPY } from '../../lib/actionConfig'
import type { ActionDraftController } from '../../hooks/useActionDraft'
import { resolveOperandDatatype } from '../../lib/actionValidation'
import { ActionCriteriaRow } from './ActionCriteriaRow'
import { ActionSection } from './ActionSection'

interface ActionCriteriaListProps {
  controller: ActionDraftController
  snapshot: GraphSnapshot | null
}

/** 提交校验块：条件之间是「且」，全部成立才允许执行。 */
export function ActionCriteriaList({ controller, snapshot }: ActionCriteriaListProps) {
  const { conditions, parameters } = controller.draft

  return (
    <ActionSection
      title={ACTION_SECTION_COPY.criteria}
      count={conditions.length}
      hint={ACTION_COPY.criteriaHint}
      actionLabel="新增校验"
      onAdd={controller.addCondition}
      empty={!conditions.length}
      emptyCopy={ACTION_COPY.criteriaEmpty}
    >
      {conditions.map((condition) => {
        const left = resolveOperandDatatype(controller.draft, condition.left, snapshot)
        return (
          <ActionCriteriaRow
            key={condition.key}
            condition={condition}
            parameters={parameters}
            snapshot={snapshot}
            issue={controller.issueFor(condition.key)}
            leftDatatype={left.ok ? left.datatype : null}
            leftEnumValues={left.ok ? left.enumValues : null}
            onChange={(patch) => controller.updateCondition(condition.key, patch)}
            onChangeOperand={(side, patch) => controller.updateOperand(condition.key, side, patch)}
            onRemove={() => controller.removeCondition(condition.key)}
          />
        )
      })}
    </ActionSection>
  )
}
