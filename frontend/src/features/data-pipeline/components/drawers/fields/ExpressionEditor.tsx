import { ActionIcon, Button } from '@mantine/core'
import { Plus, Trash2 } from 'lucide-react'
import {
  NativeSelectField,
  TextField,
} from '../../../../../shared/components/form/controls'
import {
  emptyExpression,
  normalizeExpression,
  type ExpressionNode,
} from '../../../lib/expressionAst'
import {
  COMPARISON_OPERATOR_OPTIONS,
  DATA_TYPE_OPTIONS,
  EDITOR_COPY,
  EXPRESSION_KIND_OPTIONS,
} from '../../../lib/editorOptions'
import { LiteralValueEditor } from './LiteralValueEditor'

interface EditorProps {
  value: unknown
  onChange: (value: ExpressionNode) => void
}

interface NodeProps {
  node: ExpressionNode
  onChange: (node: ExpressionNode) => void
  onRemove?: () => void
}

function ExpressionEditorNode({ node, onChange, onRemove }: NodeProps) {
  function update(patch: Partial<ExpressionNode>) {
    onChange({ ...node, ...patch })
  }

  return (
    <div className="pipeline-ast-node">
      <div className="pipeline-ast-row">
        <NativeSelectField
          className="pipeline-ast-control"
          aria-label={EDITOR_COPY.expressionKind}
          value={node.kind}
          data={EXPRESSION_KIND_OPTIONS}
          onChange={(event) => onChange(emptyExpression(event.target.value))}
        />
        {onRemove ? (
          <ActionIcon type="button" variant="subtle" color="red" aria-label={EDITOR_COPY.remove} onClick={onRemove}>
            <Trash2 size={13} />
          </ActionIcon>
        ) : null}
      </div>
      {node.kind === 'field' ? (
        <TextField
          className="pipeline-ast-control"
          aria-label={EDITOR_COPY.field}
          value={node.field ?? ''}
          placeholder={EDITOR_COPY.fieldPlaceholder}
          onChange={(event) => update({ field: event.target.value })}
        />
      ) : null}
      {node.kind === 'literal' ? (
        <LiteralValueEditor
          value={{ kind: 'literal', dataType: node.dataType ?? 'string', value: node.value }}
          onChange={(literal) => update({ value: literal.value, dataType: literal.dataType })}
        />
      ) : null}
      {node.kind === 'cast' ? (
        <div className="pipeline-ast-row">
          <ExpressionEditorNode
            node={node.expression ?? emptyExpression()}
            onChange={(expression) => update({ expression })}
          />
          <NativeSelectField
            className="pipeline-ast-control"
            aria-label={EDITOR_COPY.castType}
            value={node.dataType ?? 'string'}
            data={DATA_TYPE_OPTIONS.map((type) => ({ value: type, label: type }))}
            onChange={(event) => update({ dataType: event.target.value })}
          />
        </div>
      ) : null}
      {node.kind === 'case' ? (
        <div className="pipeline-ast-list">
          {(node.branches ?? []).map((branch, index) => (
            <div className="pipeline-ast-branch" key={index}>
              <span>{EDITOR_COPY.thenValue}</span>
              <ExpressionEditorNode
                node={branch.when}
                onChange={(when) => update({
                  branches: (node.branches ?? []).map((item, itemIndex) => itemIndex === index ? { ...item, when } : item),
                })}
              />
              <ExpressionEditorNode
                node={branch.then}
                onChange={(then) => update({
                  branches: (node.branches ?? []).map((item, itemIndex) => itemIndex === index ? { ...item, then } : item),
                })}
              />
              {(node.branches?.length ?? 0) > 1 ? (
                <ActionIcon
                  type="button"
                  variant="subtle"
                  color="red"
                  aria-label={EDITOR_COPY.remove}
                  onClick={() => update({ branches: (node.branches ?? []).filter((_, itemIndex) => itemIndex !== index) })}
                >
                  <Trash2 size={12} />
                </ActionIcon>
              ) : null}
            </div>
          ))}
          <Button
            type="button"
            variant="default"
            size="compact-sm"
            leftSection={<Plus size={12} />}
            onClick={() => update({ branches: [...(node.branches ?? []), { when: emptyExpression(), then: emptyExpression() }] })}
          >
            {EDITOR_COPY.addBranch}
          </Button>
          <span>{EDITOR_COPY.otherwise}</span>
          <ExpressionEditorNode
            node={node.otherwise ?? emptyExpression()}
            onChange={(otherwise) => update({ otherwise })}
          />
        </div>
      ) : null}
      {node.kind === 'compare' ? (
        <div className="pipeline-ast-list">
          <NativeSelectField
            className="pipeline-ast-control"
            aria-label={EDITOR_COPY.operator}
            value={node.op ?? 'eq'}
            data={COMPARISON_OPERATOR_OPTIONS}
            onChange={(event) => update({ op: event.target.value })}
          />
          <span>{EDITOR_COPY.left}</span>
          <ExpressionEditorNode node={node.left ?? emptyExpression()} onChange={(left) => update({ left })} />
          <span>{EDITOR_COPY.right}</span>
          <ExpressionEditorNode node={node.right ?? emptyExpression()} onChange={(right) => update({ right })} />
        </div>
      ) : null}
      {node.kind === 'logical' ? (
        <div className="pipeline-ast-list">
          <NativeSelectField
            className="pipeline-ast-control"
            aria-label={EDITOR_COPY.operator}
            value={node.op ?? 'and'}
            data={[
              { value: 'and', label: '全部满足' },
              { value: 'or', label: '任一满足' },
            ]}
            onChange={(event) => update({ op: event.target.value })}
          />
          {(node.expressions ?? []).map((expression, index) => (
            <ExpressionEditorNode
              key={index}
              node={expression}
              onChange={(next) => update({
                expressions: (node.expressions ?? []).map((item, itemIndex) => itemIndex === index ? next : item),
              })}
              onRemove={(node.expressions?.length ?? 0) > 2 ? () => update({
                expressions: (node.expressions ?? []).filter((_, itemIndex) => itemIndex !== index),
              }) : undefined}
            />
          ))}
          <Button
            type="button"
            variant="default"
            size="compact-sm"
            leftSection={<Plus size={12} />}
            onClick={() => update({ expressions: [...(node.expressions ?? []), emptyExpression()] })}
          >
            {EDITOR_COPY.addExpression}
          </Button>
        </div>
      ) : null}
      {node.kind === 'isNull' ? (
        <ExpressionEditorNode
          node={node.expression ?? emptyExpression()}
          onChange={(expression) => update({ expression })}
        />
      ) : null}
    </div>
  )
}

export function ExpressionEditor({ value, onChange }: EditorProps) {
  return (
    <div className="pipeline-ast-editor" data-editor="expression-tree">
      <ExpressionEditorNode node={normalizeExpression(value)} onChange={onChange} />
    </div>
  )
}
