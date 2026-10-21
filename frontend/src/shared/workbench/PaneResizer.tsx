import type { PointerEvent } from 'react'

interface PaneResizerProps {
  dragging: boolean
  ariaLabel: string
  onPointerDown: (event: PointerEvent<HTMLDivElement>) => void
}

/** 工作台分栏把手；具体宽度由 useWorkbenchPaneWidth 管。 */
export function PaneResizer({ dragging, ariaLabel, onPointerDown }: PaneResizerProps) {
  return (
    <div
      className={`pane-resizer ${dragging ? 'is-dragging' : ''}`}
      role="separator"
      aria-orientation="vertical"
      aria-label={ariaLabel}
      onPointerDown={onPointerDown}
    />
  )
}
