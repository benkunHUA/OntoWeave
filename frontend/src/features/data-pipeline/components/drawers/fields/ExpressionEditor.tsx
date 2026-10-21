import { Plus, Trash2 } from 'lucide-react'
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
        <select
          aria-label={EDITOR_COPY.expressionKind}
          value={node.kind}
          onChange={(event) => onChange(emptyExpression(event.target.value))}
        >
          {EXPRESSION_KIND_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        {onRemove ? (
          <button type="button" className="icon-button icon-button--quiet" aria-label={EDITOR_COPY.remove} onClick={onRemove}>
            <Trash2 size={13} />
          </button>
        ) : null}
      </div>
      {node.kind === 'field' ? (
        <input
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
          <select
            aria-label={EDITOR_COPY.castType}
            value={node.dataType ?? 'string'}
            onChange={(event) => update({ dataType: event.target.value })}
          >
            {DATA_TYPE_OPTIONS.map((type) => <option key={type} value={type}>{type}</option>)}
          </select>
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
                <button
                  type="button"
                  className="icon-button icon-button--quiet"
                  aria-label={EDITOR_COPY.remove}
                  onClick={() => update({ branches: (node.branches ?? []).filter((_, itemIndex) => itemIndex !== index) })}
                >
                  <Trash2 size={12} />
                </button>
              ) : null}
            </div>
          ))}
          <button
            type="button"
            className="pipeline-add-row"
            onClick={() => update({ branches: [...(node.branches ?? []), { when: emptyExpression(), then: emptyExpression() }] })}
          >
            <Plus size={12} />{EDITOR_COPY.addBranch}
          </button>
          <span>{EDITOR_COPY.otherwise}</span>
          <ExpressionEditorNode
            node={node.otherwise ?? emptyExpression()}
            onChange={(otherwise) => update({ otherwise })}
          />
        </div>
      ) : null}
      {node.kind === 'compare' ? (
        <div className="pipeline-ast-list">
          <select aria-label={EDITOR_COPY.operator} value={node.op ?? 'eq'} onChange={(event) => update({ op: event.target.value })}>
            {COMPARISON_OPERATOR_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <span>{EDITOR_COPY.left}</span>
          <ExpressionEditorNode node={node.left ?? emptyExpression()} onChange={(left) => update({ left })} />
          <span>{EDITOR_COPY.right}</span>
          <ExpressionEditorNode node={node.right ?? emptyExpression()} onChange={(right) => update({ right })} />
        </div>
      ) : null}
      {node.kind === 'logical' ? (
        <div className="pipeline-ast-list">
          <select aria-label={EDITOR_COPY.operator} value={node.op ?? 'and'} onChange={(event) => update({ op: event.target.value })}>
            <option value="and">全部满足</option>
            <option value="or">任一满足</option>
          </select>
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
          <button
            type="button"
            className="pipeline-add-row"
            onClick={() => update({ expressions: [...(node.expressions ?? []), emptyExpression()] })}
          >
            <Plus size={12} />{EDITOR_COPY.addExpression}
          </button>
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
