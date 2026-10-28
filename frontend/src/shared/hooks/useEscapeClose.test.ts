// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, renderHook } from '../../test/render'
import { useEscapeClose } from './useEscapeClose'

afterEach(cleanup)

describe('useEscapeClose', () => {
  it('ignores non-Escape keys', () => {
    const onClose = vi.fn()
    renderHook(() => useEscapeClose(true, onClose))

    fireEvent.keyDown(document.body, { key: 'Backspace' })

    expect(onClose).not.toHaveBeenCalled()
  })

  it('closes on Escape', () => {
    const onClose = vi.fn()
    renderHook(() => useEscapeClose(true, onClose))

    fireEvent.keyDown(document.body, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
