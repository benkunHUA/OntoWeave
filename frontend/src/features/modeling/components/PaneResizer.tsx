import { useModeling } from '../context/modelingContext'
import type { PaneKey } from '../lib/paneLayout'

interface PaneResizerProps {
  pane: PaneKey
  ariaLabel: string
}

/** 两条分隔线共用：按下开始拖，宽度由 usePaneLayout 统一管。 */
export function PaneResizer({ pane, ariaLabel }: PaneResizerProps) {
  const { panes } = useModeling()

  return (
    <div
      className={`pane-resizer pane-resizer--${pane} ${
        panes.draggingPane === pane ? 'is-dragging' : ''
      }`}
      role="separator"
      aria-orientation="vertical"
      aria-label={ariaLabel}
      onPointerDown={(event) => panes.startResize(pane, event)}
    />
  )
}
