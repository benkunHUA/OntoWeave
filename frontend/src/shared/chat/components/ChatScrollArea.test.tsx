// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render } from '../../../test/render'
import { ChatScrollArea } from './ChatScrollArea'

class MockResizeObserver {
  static instances: MockResizeObserver[] = []

  private readonly callback: ResizeObserverCallback
  readonly observe = vi.fn()
  readonly unobserve = vi.fn()
  readonly disconnect = vi.fn()

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
    MockResizeObserver.instances.push(this)
  }

  fire() {
    this.callback([], this as unknown as ResizeObserver)
  }
}

describe('ChatScrollArea', () => {
  beforeEach(() => {
    MockResizeObserver.instances = []
    vi.stubGlobal('ResizeObserver', MockResizeObserver)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('follows streamed content while stuck to the bottom and pauses after scrolling up', () => {
    const { container } = render(
      <ChatScrollArea>
        <div>流式内容</div>
      </ChatScrollArea>,
    )
    const scroll = container.querySelector<HTMLElement>('.message-scroll')
    expect(scroll).not.toBeNull()
    if (!scroll) return

    let scrollTop = 400
    let scrollHeight = 600
    Object.defineProperty(scroll, 'scrollHeight', {
      configurable: true,
      get: () => scrollHeight,
    })
    Object.defineProperty(scroll, 'clientHeight', { configurable: true, get: () => 200 })
    Object.defineProperty(scroll, 'scrollTop', {
      configurable: true,
      get: () => scrollTop,
      set: (value: number) => {
        scrollTop = value
      },
    })

    const observer = MockResizeObserver.instances.at(-1)
    expect(observer).toBeTruthy()
    if (!observer) return

    act(() => {
      scroll.dispatchEvent(new Event('scroll'))
    })
    act(() => {
      scrollHeight = 700
      observer.fire()
    })
    expect(scrollTop).toBe(500)

    act(() => {
      scrollTop = 100
      scroll.dispatchEvent(new Event('scroll'))
    })
    act(() => {
      scrollHeight = 900
      observer.fire()
    })
    expect(scrollTop).toBe(100)

    act(() => {
      scrollTop = 700
      scroll.dispatchEvent(new Event('scroll'))
    })
    act(() => {
      scrollHeight = 1100
      observer.fire()
    })
    expect(scrollTop).toBe(900)
  })
})
