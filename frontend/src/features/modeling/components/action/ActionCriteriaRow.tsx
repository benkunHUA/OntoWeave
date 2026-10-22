import { ActionIcon } from '@mantine/core'
import { Trash2 } from 'lucide-react'
import { NativeSelectField, TextField } from '../../../../shared/components/form/controls'
import type { GraphSnapshot } from '../../../../shared/types'
import { OPERATOR_OPTIONS, operatorLabel, operatorsForDatatype } from '../../lib/actionConfig'
import type {
  ActionConditionDraft,
  ActionOperandDraft,
  ActionParameterDraft,
} from '../../lib/actionDraft'
import { ActionOperandFields } from './ActionOperandFields'

interface ActionCriteriaRowProps {
  condition: ActionConditionDraft
  parameters: ActionParameterDraft[]
  snapshot: GraphSnapshot | null
  issue: string | null
  /** 左侧自带的数据类型与候选值：运算符清单与右侧固定值的类型都由它定。 */
  leftDatatype: string | null
  leftEnumValues: string[] | null
  onChange: (patch: Partial<ActionConditionDraft>) => void
  onChangeOperand: (side: 'left' | 'right', patch: Partial<ActionOperandDraft>) => void
  onRemove: () => void
}

/** 一条校验条件：左操作数 + 运算符 + 右操作数 + 失败提示。 */
export function ActionCriteriaRow({
  condition,
  parameters,
  snapshot,
  issue,
  leftDatatype,
  leftEnumValues,
  onChange,
  onChangeOperand,
  onRemove,
}: ActionCriteriaRowProps) {
  const operators = operatorsForDatatype(leftDatatype)
  const unary = operators.find((item) => item.value === condition.operator)?.unary ?? false
  const operatorData = OPERATOR_OPTIONS.filter((item) =>
    operators.some((allowed) => allowed.value === item.value),
  ).map((item) => ({ value: item.value, label: item.label }))
  if (!operators.some((item) => item.value === condition.operator)) {
    operatorData.unshift({
      value: condition.operator,
      label: operatorLabel(condition.operator),
    })
  }

  return (
    <div className={`action-row${issue ? ' is-invalid' : ''}`}>
      <div className="action-row__head">
        <span className="action-row__label">当</span>
        <NativeSelectField
          className="action-row__control action-row__control--narrow"
          value={condition.operator}
          aria-label="运算符"
          data={operatorData}
          onChange={(event) => onChange({ operator: event.target.value })}
        />
        <ActionIcon
          type="button"
          variant="subtle"
          color="red"
          size="sm"
          aria-label="删除这条校验"
          onClick={onRemove}
        >
          <Trash2 size={15} />
        </ActionIcon>
      </div>

      <ActionOperandFields
        operand={condition.left}
        parameters={parameters}
        snapshot={snapshot}
        allowStatic={false}
        staticDatatype={null}
        staticEnumValues={null}
        sideLabel="左侧"
        onChange={(patch) => onChangeOperand('left', patch)}
      />

      {unary ? null : (
        <ActionOperandFields
          operand={condition.right}
          parameters={parameters}
          snapshot={snapshot}
          allowStatic
          staticDatatype={leftDatatype}
          staticEnumValues={leftEnumValues}
          sideLabel="右侧"
          onChange={(patch) => onChangeOperand('right', patch)}
        />
      )}

      <TextField
        className="action-row__control action-row__control--wide"
        value={condition.failureMessage}
        aria-label="校验失败时的提示"
        placeholder="校验不通过时提示什么"
        onChange={(event) => onChange({ failureMessage: event.target.value })}
      />

      {issue ? <p className="action-row__issue">{issue}</p> : null}
    </div>
  )
}
