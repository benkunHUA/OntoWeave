import { useCallback, useEffect, useState, type CSSProperties, type PointerEvent } from 'react'

interface WorkbenchPaneWidthOptions {
  storageKey: string
  defaultWidth: number
  min: number
  max: number
  cssVariable: string
}

function clamp(width: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, width))
}

export function useWorkbenchPaneWidth({
  storageKey,
  defaultWidth,
  min,
  max,
  cssVariable,
}: WorkbenchPaneWidthOptions) {
  const readWidth = useCallback(() => {
    const stored = Number(window.localStorage.getItem(storageKey))
    return Number.isFinite(stored) && stored > 0 ? clamp(stored, min, max) : defaultWidth
  }, [defaultWidth, max, min, storageKey])
  const [width, setWidth] = useState(readWidth)
  const [dragging, setDragging] = useState(false)
  const [drag, setDrag] = useState<{ startX: number; startWidth: number } | null>(null)

  useEffect(() => {
    if (!drag) return
    const activeDrag = drag
    function handleMove(event: globalThis.PointerEvent) {
      setWidth(clamp(activeDrag.startWidth - (event.clientX - activeDrag.startX), min, max))
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
  }, [drag, max, min])

  useEffect(() => {
    window.localStorage.setItem(storageKey, String(width))
  }, [storageKey, width])

  const startResize = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      setDrag({ startX: event.clientX, startWidth: width })
      setDragging(true)
    },
    [width],
  )

  const styleVars = { [cssVariable]: `${width}px` } as CSSProperties
  return { width, dragging, startResize, styleVars }
}
