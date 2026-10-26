import { Handle, Position, useUpdateNodeInternals, type NodeProps } from '@xyflow/react'
import { AlertTriangle, CircleAlert, CircleCheck, KeyRound } from 'lucide-react'
import { CANVAS_COPY } from './copy'
import type { MappingFlowNode } from './types'

export function MappingNode({ id, data }: NodeProps<MappingFlowNode>) {
  const updateNodeInternals = useUpdateNodeInternals()
  const left = data.side === 'left'
  return (
    <article className={`mapping-node is-${data.side}`}>
      <header className="mapping-node__head">
        <span className="mapping-node__icon"><KeyRound size={14} /></span>
        <span>
          <strong>{data.title}</strong>
          <small>{data.subtitle}</small>
        </span>
        <span className="mapping-node__count">{data.fields.length}</span>
      </header>

      {data.keyControls ? (
        <div className="mapping-node__keys nodrag">
          <label>
            <span>{CANVAS_COPY.primaryLabel}</span>
            <select
              aria-label={CANVAS_COPY.primaryLabel}
              value={data.keyControls.primaryValue}
              onChange={(event) => data.keyControls?.onPrimaryChange(event.target.value)}
            >
              {data.keyControls.primaryOptions.map((option) => (
                <option key={option.id} value={option.id}>{option.label}</option>
              ))}
            </select>
          </label>
          <label>
            <span>{CANVAS_COPY.titleLabel}</span>
            <select
              aria-label={CANVAS_COPY.titleLabel}
              value={data.keyControls.titleValue ?? ''}
              onChange={(event) => data.keyControls?.onTitleChange(event.target.value || null)}
            >
              <option value="">{CANVAS_COPY.noTitle}</option>
              {data.keyControls.titleOptions.map((option) => (
                <option key={option.id} value={option.id}>{option.label}</option>
              ))}
            </select>
          </label>
        </div>
      ) : null}

      <div
        className="mapping-node__fields nowheel"
        onScroll={() => updateNodeInternals(id)}
        role="list"
        aria-label={CANVAS_COPY.fieldAria(data.title)}
      >
        {data.fields.length ? data.fields.map((field) => (
          <div
            className={`mapping-field ${field.mapped ? 'is-mapped' : ''} ${field.blocking ? 'is-blocking' : ''} ${field.warning ? 'is-warning' : ''}`}
            key={field.id}
            role="listitem"
          >
            {left ? (
              <Handle
                id={field.id}
                type="source"
                position={Position.Right}
                className={`mapping-handle ${field.disabled ? 'is-disabled' : ''}`}
                isConnectable={!field.disabled}
                aria-label={`${field.label} 连接桩`}
              />
            ) : (
              <Handle
                id={field.id}
                type="target"
                position={Position.Left}
                className={`mapping-handle ${field.disabled ? 'is-disabled' : ''}`}
                isConnectable={!field.disabled}
                aria-label={`${field.label} 连接桩`}
              />
            )}
            <span className="mapping-field__title">
              <strong>{field.label}</strong>
              <small>{field.typeLabel}</small>
            </span>
            <span className="mapping-field__meta">
              {field.meta ? <small>{field.meta}</small> : null}
              <span className="mapping-field__badges">
                {field.badges.map((badge) => <em key={badge}>{badge}</em>)}
              </span>
            </span>
            {field.blocking ? <CircleAlert size={13} className="is-error" /> : null}
            {!field.blocking && field.warning ? <AlertTriangle size={13} className="is-warning" /> : null}
            {field.mapped && !field.blocking && !field.warning ? <CircleCheck size={13} className="is-ok" /> : null}
          </div>
        )) : (
          <div className="mapping-node__empty">{CANVAS_COPY.noDefinition}</div>
        )}
      </div>
    </article>
  )
}
