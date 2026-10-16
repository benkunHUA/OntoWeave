import { useCallback, useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react'
import {
  clampPaneWidth,
  readPaneWidths,
  writePaneWidths,
  type PaneKey,
  type PaneWidths,
} from '../lib/paneLayout'

/** 会话栏的收起与两条分隔线的拖拽。宽度存本地，下次打开还是用户摆好的样子。 */
export function usePaneLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [widths, setWidths] = useState<PaneWidths>(readPaneWidths)
  const [draggingPane, setDraggingPane] = useState<PaneKey | null>(null)
  const dragRef = useRef<{ key: PaneKey; startX: number; startWidth: number } | null>(null)

  useEffect(() => {
    if (!draggingPane) return
    function handlePointerMove(event: PointerEvent) {
      const drag = dragRef.current
      if (!drag) return
      const delta = event.clientX - drag.startX
      setWidths((current) => ({
        ...current,
        [drag.key]: clampPaneWidth(drag.key, drag.startWidth + delta),
      }))
    }
    function handlePointerUp() {
      setDraggingPane(null)
    }
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [draggingPane])

  useEffect(() => {
    writePaneWidths(widths)
  }, [widths])

  const startResize = useCallback(
    (key: PaneKey, event: ReactPointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      dragRef.current = { key, startX: event.clientX, startWidth: widths[key] }
      setDraggingPane(key)
    },
    [widths],
  )

  const toggleCollapsed = useCallback(() => setCollapsed((value) => !value), [])

  const styleVars = {
    '--conv-pane-w': `${widths.conversations}px`,
    '--chat-pane-w': `${widths.chat}px`,
  } as CSSProperties

  return { collapsed, toggleCollapsed, draggingPane, startResize, styleVars }
}
