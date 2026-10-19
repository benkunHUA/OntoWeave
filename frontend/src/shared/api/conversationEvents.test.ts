import { describe, expect, it } from 'vitest'
import { parseSseFrame } from './conversationEvents'

describe('parseSseFrame', () => {
  it('解析事件名与 JSON 载荷', () => {
    const frame = parseSseFrame('event: conversation.event\ndata: {"id":"e1"}')

    expect(frame).toEqual({ event: 'conversation.event', data: '{"id":"e1"}' })
  })

  it('识别心跳帧', () => {
    expect(parseSseFrame('event: heartbeat\ndata: {}')?.event).toBe('heartbeat')
  })

  it('没有 data 的块不产生事件', () => {
    expect(parseSseFrame('event: conversation.event')).toBeNull()
  })
})
