import { Plus, Trash2 } from 'lucide-react'
import {
  changeConditionKind,
  changeConditionOperator,
  emptyCompare,
  literalValue,
  normalizeCondition,
  type CompareCondition,
  type ConditionNode,
} from '../../../lib/conditionAst'
import {
  CONDITION_KIND_OPTIONS,
  CONDITION_OPERATOR_OPTIONS,
  EDITOR_COPY,
} from '../../../lib/editorOptions'
import { LiteralValueEditor } from './LiteralValueEditor'

interface EditorProps {
  value: unknown
  onChange: (value: ConditionNode) => void
}

interface NodeProps {
  node: ConditionNode
  onChange: (node: ConditionNode) => void
  onRemove?: () => void
}

function CompareEditor({ node, onChange }: { node: CompareCondition; onChange: (node: CompareCondition) => void }) {
  const needsValue = !['in', 'is_null', 'is_not_null'].includes(node.op)

  return (
    <div className="pipeline-ast-row">
      <input
        aria-label={EDITOR_COPY.field}
        value={node.field}
        placeholder={EDITOR_COPY.fieldPlaceholder}
        onChange={(event) => onChange({ ...node, field: event.target.value })}
      />
      <select
        aria-label={EDITOR_COPY.operator}
        value={node.op}
        onChange={(event) => onChange(changeConditionOperator(node, event.target.value))}
      >
        {CONDITION_OPERATOR_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
      {needsValue && node.value ? (
        <LiteralValueEditor
          value={node.value}
          onChange={(value) => onChange({ ...node, value })}
        />
      ) : null}
      {node.op === 'in' ? (
        <div className="pipeline-ast-list">
          {(node.values ?? []).map((operand, index) => (
            <span className="pipeline-ast-row" key={index}>
              <LiteralValueEditor
                value={operand}
                onChange={(next) => onChange({
                  ...node,
                  values: (node.values ?? []).map((item, itemIndex) => itemIndex === index ? next : item),
                })}
              />
              <button
                type="button"
                className="icon-button icon-button--quiet"
                aria-label={EDITOR_COPY.remove}
                onClick={() => onChange({
                  ...node,
                  values: (node.values ?? []).filter((_, itemIndex) => itemIndex !== index),
                })}
              >
                <Trash2 size={12} />
              </button>
            </span>
          ))}
          <button
            type="button"
            className="pipeline-add-row"
            onClick={() => onChange({ ...node, values: [...(node.values ?? []), literalValue()] })}
          >
            <Plus size={12} />{EDITOR_COPY.addValue}
          </button>
        </div>
      ) : null}
    </div>
  )
}

function ConditionNodeEditor({ node, onChange, onRemove }: NodeProps) {
  return (
    <div className="pipeline-ast-node">
      <div className="pipeline-ast-row">
        <select
          aria-label={EDITOR_COPY.conditionKind}
          value={node.kind}
          onChange={(event) => onChange(changeConditionKind(node, event.target.value))}
        >
          {CONDITION_KIND_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        {onRemove ? (
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={EDITOR_COPY.remove}
            onClick={onRemove}
          >
            <Trash2 size={13} />
          </button>
        ) : null}
      </div>
      {node.kind === 'compare' ? (
        <CompareEditor node={node} onChange={onChange} />
      ) : (
        <div className="pipeline-ast-list">
          {node.conditions.map((child, index) => (
            <ConditionNodeEditor
              key={index}
              node={child}
              onChange={(next) => onChange({
                ...node,
                conditions: node.conditions.map((item, itemIndex) => itemIndex === index ? next : item),
              })}
              onRemove={node.conditions.length > 2 ? () => onChange({
                ...node,
                conditions: node.conditions.filter((_, itemIndex) => itemIndex !== index),
              }) : undefined}
            />
          ))}
          <button
            type="button"
            className="pipeline-add-row"
            onClick={() => onChange({ ...node, conditions: [...node.conditions, emptyCompare()] })}
          >
            <Plus size={12} />{EDITOR_COPY.addCondition}
          </button>
        </div>
      )}
    </div>
  )
}

export function ConditionTreeEditor({ value, onChange }: EditorProps) {
  const node = normalizeCondition(value)
  return (
    <div className="pipeline-ast-editor" data-editor="condition-tree">
      <ConditionNodeEditor node={node} onChange={onChange} />
    </div>
  )
}
