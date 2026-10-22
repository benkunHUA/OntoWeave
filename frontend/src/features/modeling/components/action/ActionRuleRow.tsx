import { ActionIcon, Button } from '@mantine/core'
import { Plus, Trash2 } from 'lucide-react'
import { NativeSelectField } from '../../../../shared/components/form/controls'
import type { GraphSnapshot } from '../../../../shared/types'
import {
  ACTION_COPY,
  RULE_KIND_OPTIONS,
  ruleKindOption,
} from '../../lib/actionConfig'
import type { ActionAssignmentDraft, ActionParameterDraft, ActionRuleDraft } from '../../lib/actionDraft'
import { objectParameterOptions, objectTypeOptions, relationOptions } from '../../lib/actionOptions'
import { ActionAssignmentRow } from './ActionAssignmentRow'

interface ActionRuleRowProps {
  rule: ActionRuleDraft
  parameters: ActionParameterDraft[]
  snapshot: GraphSnapshot | null
  issue: string | null
  issueFor: (key: string) => string | null
  onChange: (patch: Partial<ActionRuleDraft>) => void
  onRemove: () => void
  onAddAssignment: () => void
  onChangeAssignment: (key: string, patch: Partial<ActionAssignmentDraft>) => void
  onRemoveAssignment: (key: string) => void
}

/** 一条规则：作用对象 / 两端参数 + 字段赋值。 */
export function ActionRuleRow({
  rule,
  parameters,
  snapshot,
  issue,
  issueFor,
  onChange,
  onRemove,
  onAddAssignment,
  onChangeAssignment,
  onRemoveAssignment,
}: ActionRuleRowProps) {
  const option = ruleKindOption(rule.ruleKind)
  const relation = snapshot?.relations.find((item) => item.id === rule.targetRelationId) ?? null

  return (
    <div className={`action-row${issue ? ' is-invalid' : ''}`}>
      <div className="action-row__head">
        <NativeSelectField
          className="action-row__control"
          value={rule.ruleKind}
          aria-label="规则种类"
          data={RULE_KIND_OPTIONS.map((item) => ({ value: item.value, label: item.label }))}
          // 改种类等于换语义：已保存的规则不给改，删掉重建更清楚。
          disabled={rule.id !== null}
          title={rule.id !== null ? ACTION_COPY.newRuleHint : option?.hint}
          onChange={(event) => onChange({ ruleKind: event.target.value })}
        />
        <ActionIcon
          type="button"
          variant="subtle"
          color="red"
          size="sm"
          aria-label="删除这条规则"
          onClick={onRemove}
        >
          <Trash2 size={15} />
        </ActionIcon>
      </div>

      <div className="action-row__fields">
        {option?.family === 'object' ? (
          <NativeSelectField
            className="action-row__control"
            value={rule.targetObjectTypeId}
            aria-label="规则作用的对象类型"
            data={[
              { value: '', label: '选对象类型' },
              ...objectTypeOptions(snapshot),
            ]}
            onChange={(event) =>
              onChange({ targetObjectTypeId: event.target.value, sourceParameterId: '' })
            }
          />
        ) : (
          <NativeSelectField
            className="action-row__control"
            value={rule.targetRelationId}
            aria-label="规则作用的关系"
            data={[
              { value: '', label: '选多对多关系' },
              ...relationOptions(snapshot),
            ]}
            onChange={(event) =>
              onChange({
                targetRelationId: event.target.value,
                sourceParameterId: '',
                targetParameterId: '',
              })
            }
          />
        )}

        {option?.family === 'object' && rule.ruleKind !== 'create_object' ? (
          <NativeSelectField
            className="action-row__control"
            value={rule.sourceParameterId}
            aria-label="规则用的对象参数"
            data={[
              { value: '', label: '选对象参数' },
              ...objectParameterOptions(parameters, snapshot, rule.targetObjectTypeId),
            ]}
            disabled={!rule.targetObjectTypeId}
            onChange={(event) => onChange({ sourceParameterId: event.target.value })}
          />
        ) : null}

        {option?.family === 'link' && relation ? (
          <>
            <NativeSelectField
              className="action-row__control"
              value={rule.sourceParameterId}
              aria-label="链接起点的对象参数"
              data={[
                { value: '', label: '起点参数' },
                ...objectParameterOptions(parameters, snapshot, relation.source_type_id),
              ]}
              onChange={(event) => onChange({ sourceParameterId: event.target.value })}
            />
            <NativeSelectField
              className="action-row__control"
              value={rule.targetParameterId}
              aria-label="链接终点的对象参数"
              data={[
                { value: '', label: '终点参数' },
                ...objectParameterOptions(parameters, snapshot, relation.target_type_id),
              ]}
              onChange={(event) => onChange({ targetParameterId: event.target.value })}
            />
          </>
        ) : null}
      </div>

      {option ? <p className="action-row__hint">{option.hint}</p> : null}

      {option?.assignable ? (
        <div className="action-row__assignments">
          <div className="action-row__subhead">
            <span>字段赋值</span>
            <Button
              type="button"
              variant="subtle"
              size="compact-sm"
              leftSection={<Plus size={14} />}
              onClick={onAddAssignment}
            >
              新增赋值
            </Button>
          </div>
          {rule.assignments.length ? (
            rule.assignments.map((assignment) => (
              <ActionAssignmentRow
                key={assignment.key}
                assignment={assignment}
                targetObjectTypeId={rule.targetObjectTypeId}
                parameters={parameters}
                snapshot={snapshot}
                issue={issueFor(assignment.key)}
                onChange={(patch) => onChangeAssignment(assignment.key, patch)}
                onRemove={() => onRemoveAssignment(assignment.key)}
              />
            ))
          ) : (
            <p className="action-section__empty">还没有赋值，这个规则不会改任何字段</p>
          )}
        </div>
      ) : null}

      {issue ? <p className="action-row__issue">{issue}</p> : null}
    </div>
  )
}
