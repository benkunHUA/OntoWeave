import { useEffect, useLayoutEffect, useRef } from 'react'

const escapeStack: symbol[] = []

export function useEscapeClose(active: boolean, onClose: () => void) {
  const closeRef = useRef(onClose)

  useLayoutEffect(() => {
    closeRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!active) return
    const token = Symbol('escape-layer')
    escapeStack.push(token)
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || event.defaultPrevented || escapeStack.at(-1) !== token) return
      event.preventDefault()
      event.stopPropagation()
      closeRef.current()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      const index = escapeStack.lastIndexOf(token)
      if (index >= 0) escapeStack.splice(index, 1)
    }
  }, [active])
}
