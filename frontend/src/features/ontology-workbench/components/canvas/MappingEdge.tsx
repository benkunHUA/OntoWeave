import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react'
import { X } from 'lucide-react'

export interface MappingEdgeData extends Record<string, unknown> {
  label: string
  onRemove: (edgeId: string) => void
}

export type MappingFlowEdge = Edge<MappingEdgeData, 'mapping'>

export function MappingEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
}: EdgeProps<MappingFlowEdge>) {
  const [path, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    curvature: 0.28,
  })
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        className={`mapping-edge ${selected ? 'is-selected' : ''}`}
      />
      <EdgeLabelRenderer>
        <button
          type="button"
          className="mapping-edge__remove nodrag nopan"
          style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
          aria-label={`删除连线 ${data?.label ?? id}`}
          title={`删除连线 ${data?.label ?? id}`}
          onClick={() => data?.onRemove(id)}
        >
          <X size={10} />
        </button>
      </EdgeLabelRenderer>
    </>
  )
}
