import { Handle, Position, type NodeProps } from '@xyflow/react'
import { Box, CircleCheck, Layers3, Sparkles } from 'lucide-react'
import type { OntologyFlowNode } from '../lib/flow'

export function OntologyNode({ data, selected }: NodeProps<OntologyFlowNode>) {
  const classNames = [
    'ontology-node',
    data.draft ? 'is-draft' : '',
    data.ghosted ? 'is-ghosted' : '',
    data.highlighted ? 'is-highlighted' : '',
    data.dimmed ? 'is-dimmed' : '',
    selected ? 'is-selected' : '',
  ]
    .filter(Boolean)
    .join(' ')

  const statusLabel =
    data.status === 'draft' ? '草稿' : data.status === 'approved' ? '已批准' : '已确认'
  const StatusIcon = data.status === 'confirmed' || data.status === 'approved' ? CircleCheck : Sparkles

  return (
    <div className={classNames}>
      <Handle type="target" position={Position.Left} />
      <div className="ontology-node__top">
        <span className="ontology-node__icon">
          <Box size={16} />
        </span>
        <strong>{data.label}</strong>
        <span className={`status-dot status-dot--${data.status}`}>
          <StatusIcon size={12} />
          {statusLabel}
        </span>
      </div>
      {data.description ? (
        <p>{data.description}</p>
      ) : (
        <p className="muted-line">尚未补充描述</p>
      )}
      <div className="ontology-node__meta">
        <span>
          <Layers3 size={13} />
          {data.properties} 个属性
        </span>
        {data.groupLabel ? <span>{data.groupLabel}</span> : null}
      </div>
      {data.annotation ? (
        <div className="ontology-node__annotation">{data.annotation}</div>
      ) : null}
      <Handle type="source" position={Position.Right} />
    </div>
  )
}
