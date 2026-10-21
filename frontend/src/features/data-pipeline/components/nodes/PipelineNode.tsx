import {
  Calculator,
  Columns3,
  Copy,
  Database,
  Filter,
  GitMerge,
  Merge,
  PenLine,
  Share2,
  Sigma,
} from 'lucide-react'
import { Handle, Position, type NodeProps } from '@xyflow/react'
import type { CSSProperties } from 'react'
import type { PipelineFlowNode } from '../../lib/flow'

const NODE_ICONS: Record<string, typeof Database> = {
  input: Database,
  filter: Filter,
  select_columns: Columns3,
  rename_columns: PenLine,
  derive_column: Sigma,
  deduplicate: Copy,
  join: GitMerge,
  union: Merge,
  aggregate: Calculator,
  output: Share2,
}

function portStyle(index: number, count: number): CSSProperties {
  return { top: `${((index + 1) / (count + 1)) * 100}%` }
}

export function PipelineNode({ data, selected }: NodeProps<PipelineFlowNode>) {
  const Icon = NODE_ICONS[data.node.type] ?? Database
  return (
    <article className={`pipeline-node ${selected ? 'is-selected' : ''}`}>
      {data.inputs.map((port, index) => (
        <Handle
          key={port.id}
          id={port.id}
          type="target"
          position={Position.Left}
          style={portStyle(index, data.inputs.length)}
          title={port.id}
        />
      ))}
      <div className="pipeline-node__head">
        <span className="pipeline-node__icon"><Icon size={15} /></span>
        <span className="pipeline-node__name">{data.label}</span>
      </div>
      <span className="pipeline-node__type">{data.nodeType}</span>
      {data.outputs.map((port, index) => (
        <Handle
          key={port.id}
          id={port.id}
          type="source"
          position={Position.Right}
          style={portStyle(index, data.outputs.length)}
          title={port.id}
        />
      ))}
    </article>
  )
}
