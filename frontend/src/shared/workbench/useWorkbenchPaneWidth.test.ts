// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { act, cleanup, renderHook } from '../../test/render'
import { useWorkbenchPaneWidth } from './useWorkbenchPaneWidth'

const OPTIONS = {
  storageKey: 'test.pane-width',
  defaultWidth: 400,
  min: 320,
  max: 720,
  cssVariable: '--pane-width',
} as const

function pointerDown(clientX: number): ReactPointerEvent<HTMLDivElement> {
  return { clientX, preventDefault() {} } as ReactPointerEvent<HTMLDivElement>
}

function drag(result: { current: ReturnType<typeof useWorkbenchPaneWidth> }, from: number, to: number) {
  act(() => result.current.startResize(pointerDown(from)))
  act(() => window.dispatchEvent(new MouseEvent('pointermove', { clientX: to })))
  act(() => window.dispatchEvent(new MouseEvent('pointerup')))
}

describe('useWorkbenchPaneWidth', () => {
  beforeEach(() => window.localStorage.clear())
  afterEach(cleanup)

  it('widens a pane whose resizer sits on its right edge when dragging right', () => {
    const { result } = renderHook(() => useWorkbenchPaneWidth({ ...OPTIONS, resizerSide: 'right' }))

    drag(result, 100, 180)

    expect(result.current.width).toBe(480)
  })

  it('keeps left-edge resizers widening when dragging left', () => {
    const { result } = renderHook(() => useWorkbenchPaneWidth(OPTIONS))

    drag(result, 100, 40)

    expect(result.current.width).toBe(460)
  })
})
