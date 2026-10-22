import { ActionIcon, Button } from '@mantine/core'
import { Plus, Trash2 } from 'lucide-react'
import {
  NativeSelectField,
  TextField,
} from '../../../../../shared/components/form/controls'
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
      <TextField
        className="pipeline-ast-control"
        aria-label={EDITOR_COPY.field}
        value={node.field}
        placeholder={EDITOR_COPY.fieldPlaceholder}
        onChange={(event) => onChange({ ...node, field: event.target.value })}
      />
      <NativeSelectField
        className="pipeline-ast-control"
        aria-label={EDITOR_COPY.operator}
        value={node.op}
        data={CONDITION_OPERATOR_OPTIONS}
        onChange={(event) => onChange(changeConditionOperator(node, event.target.value))}
      />
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
              <ActionIcon
                type="button"
                variant="subtle"
                color="red"
                aria-label={EDITOR_COPY.remove}
                onClick={() => onChange({
                  ...node,
                  values: (node.values ?? []).filter((_, itemIndex) => itemIndex !== index),
                })}
              >
                <Trash2 size={12} />
              </ActionIcon>
            </span>
          ))}
          <Button
            type="button"
            variant="default"
            size="compact-sm"
            leftSection={<Plus size={12} />}
            onClick={() => onChange({ ...node, values: [...(node.values ?? []), literalValue()] })}
          >
            {EDITOR_COPY.addValue}
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function ConditionNodeEditor({ node, onChange, onRemove }: NodeProps) {
  return (
    <div className="pipeline-ast-node">
      <div className="pipeline-ast-row">
        <NativeSelectField
          className="pipeline-ast-control"
          aria-label={EDITOR_COPY.conditionKind}
          value={node.kind}
          data={CONDITION_KIND_OPTIONS}
          onChange={(event) => onChange(changeConditionKind(node, event.target.value))}
        />
        {onRemove ? (
          <ActionIcon
            type="button"
            variant="subtle"
            color="red"
            aria-label={EDITOR_COPY.remove}
            onClick={onRemove}
          >
            <Trash2 size={13} />
          </ActionIcon>
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
          <Button
            type="button"
            variant="default"
            size="compact-sm"
            leftSection={<Plus size={12} />}
            onClick={() => onChange({ ...node, conditions: [...node.conditions, emptyCompare()] })}
          >
            {EDITOR_COPY.addCondition}
          </Button>
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
