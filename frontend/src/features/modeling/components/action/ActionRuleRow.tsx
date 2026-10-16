import { Plus, Trash2 } from 'lucide-react'
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
        <select
          className="action-row__input"
          value={rule.ruleKind}
          aria-label="规则种类"
          // 改种类等于换语义：已保存的规则不给改，删掉重建更清楚。
          disabled={rule.id !== null}
          title={rule.id !== null ? ACTION_COPY.newRuleHint : option?.hint}
          onChange={(event) => onChange({ ruleKind: event.target.value })}
        >
          {RULE_KIND_OPTIONS.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="field-row__remove"
          aria-label="删除这条规则"
          onClick={onRemove}
        >
          <Trash2 size={15} />
        </button>
      </div>

      <div className="action-row__fields">
        {option?.family === 'object' ? (
          <select
            className="action-row__input"
            value={rule.targetObjectTypeId}
            aria-label="规则作用的对象类型"
            onChange={(event) =>
              onChange({ targetObjectTypeId: event.target.value, sourceParameterId: '' })
            }
          >
            <option value="">选对象类型</option>
            {objectTypeOptions(snapshot).map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        ) : (
          <select
            className="action-row__input"
            value={rule.targetRelationId}
            aria-label="规则作用的关系"
            onChange={(event) =>
              onChange({
                targetRelationId: event.target.value,
                sourceParameterId: '',
                targetParameterId: '',
              })
            }
          >
            <option value="">选多对多关系</option>
            {relationOptions(snapshot).map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        )}

        {option?.family === 'object' && rule.ruleKind !== 'create_object' ? (
          <select
            className="action-row__input"
            value={rule.sourceParameterId}
            aria-label="规则用的对象参数"
            disabled={!rule.targetObjectTypeId}
            onChange={(event) => onChange({ sourceParameterId: event.target.value })}
          >
            <option value="">选对象参数</option>
            {objectParameterOptions(parameters, snapshot, rule.targetObjectTypeId).map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        ) : null}

        {option?.family === 'link' && relation ? (
          <>
            <select
              className="action-row__input"
              value={rule.sourceParameterId}
              aria-label="链接起点的对象参数"
              onChange={(event) => onChange({ sourceParameterId: event.target.value })}
            >
              <option value="">起点参数</option>
              {objectParameterOptions(parameters, snapshot, relation.source_type_id).map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
            <select
              className="action-row__input"
              value={rule.targetParameterId}
              aria-label="链接终点的对象参数"
              onChange={(event) => onChange({ targetParameterId: event.target.value })}
            >
              <option value="">终点参数</option>
              {objectParameterOptions(parameters, snapshot, relation.target_type_id).map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </>
        ) : null}
      </div>

      {option ? <p className="action-row__hint">{option.hint}</p> : null}

      {option?.assignable ? (
        <div className="action-row__assignments">
          <div className="action-row__subhead">
            <span>字段赋值</span>
            <button type="button" className="text-button" onClick={onAddAssignment}>
              <Plus size={14} />
              新增赋值
            </button>
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
