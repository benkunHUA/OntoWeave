import { useCallback, useEffect, useState, type CSSProperties, type PointerEvent } from 'react'
import { CONTEXT_PANE_WIDTH } from '../lib/constants'

const STORAGE_KEY = 'ontoweave.data-connection.context-pane-width'

function clamp(width: number): number {
  return Math.min(CONTEXT_PANE_WIDTH.max, Math.max(CONTEXT_PANE_WIDTH.min, width))
}

function readWidth(): number {
  const stored = Number(window.localStorage.getItem(STORAGE_KEY))
  return Number.isFinite(stored) && stored > 0 ? clamp(stored) : CONTEXT_PANE_WIDTH.default
}

export function useContextPaneWidth() {
  const [width, setWidth] = useState(readWidth)
  const [dragging, setDragging] = useState(false)
  const [drag, setDrag] = useState<{ startX: number; startWidth: number } | null>(null)

  useEffect(() => {
    if (!drag) return
    const activeDrag = drag
    function handleMove(event: globalThis.PointerEvent) {
      setWidth(clamp(activeDrag.startWidth - (event.clientX - activeDrag.startX)))
    }
    function handleUp() {
      setDrag(null)
      setDragging(false)
    }
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    return () => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
    }
  }, [drag])

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, String(width))
  }, [width])

  const startResize = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      setDrag({ startX: event.clientX, startWidth: width })
      setDragging(true)
    },
    [width],
  )

  const styleVars = { '--context-pane-w': `${width}px` } as CSSProperties
  return { width, dragging, startResize, styleVars }
}
