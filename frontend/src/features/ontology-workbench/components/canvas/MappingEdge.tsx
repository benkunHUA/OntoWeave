import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react'
import { ActionIcon } from '@mantine/core'
import { X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'

export interface MappingEdgeData extends Record<string, unknown> {
  label: string
  onRemove: (edgeId: string) => void
}

export type MappingFlowEdge = Edge<MappingEdgeData, 'mapping'>

const REMOVE_HIDE_DELAY_MS = 160

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
  const [removeVisible, setRemoveVisible] = useState(false)
  const hideTimerRef = useRef<number | null>(null)

  const showRemove = useCallback(() => {
    if (hideTimerRef.current !== null) {
      window.clearTimeout(hideTimerRef.current)
      hideTimerRef.current = null
    }
    setRemoveVisible(true)
  }, [])

  const scheduleHideRemove = useCallback(() => {
    if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current)
    hideTimerRef.current = window.setTimeout(() => {
      hideTimerRef.current = null
      setRemoveVisible(false)
    }, REMOVE_HIDE_DELAY_MS)
  }, [])

  useEffect(() => () => {
    if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current)
  }, [])

  return (
    <>
      <g onMouseEnter={showRemove} onMouseLeave={scheduleHideRemove}>
        <BaseEdge
          id={id}
          path={path}
          className={`mapping-edge ${selected ? 'is-selected' : ''}`}
        />
      </g>
      <EdgeLabelRenderer>
        <ActionIcon
          type="button"
          variant="filled"
          color="red"
          size="compact-xs"
          radius="xl"
          className={`mapping-edge__remove nodrag nopan ${removeVisible ? 'is-visible' : ''}`}
          style={{ left: `${labelX}px`, top: `${labelY}px` }}
          aria-label={`删除连线 ${data?.label ?? id}`}
          title={`删除连线 ${data?.label ?? id}`}
          onMouseEnter={showRemove}
          onMouseLeave={scheduleHideRemove}
          onClick={() => data?.onRemove(id)}
        >
          <X size={10} />
        </ActionIcon>
      </EdgeLabelRenderer>
    </>
  )
}
