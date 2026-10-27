import { describe, expect, it } from 'vitest'
import { formatToolArgs, formatToolPayload } from './toolPayload'

describe('formatToolPayload', () => {
  it('pretty-prints objects and arrays', () => {
    expect(formatToolPayload({ graph_id: 'g-1', limit: 5 })).toBe(
      '{\n  "graph_id": "g-1",\n  "limit": 5\n}',
    )
    expect(formatToolPayload([{ id: 1 }])).toBe('[\n  {\n    "id": 1\n  }\n]')
  })

  it('pretty-prints compact JSON strings', () => {
    expect(formatToolPayload('{"items":[{"id":1}],"total":1}')).toBe(
      '{\n  "items": [\n    {\n      "id": 1\n    }\n  ],\n  "total": 1\n}',
    )
    expect(formatToolPayload('  [1,2]  ')).toBe('[\n  1,\n  2\n]')
  })

  it('keeps plain text and invalid JSON untouched', () => {
    expect(formatToolPayload('命中 2 个对象')).toBe('命中 2 个对象')
    expect(formatToolPayload('{not json')).toBe('{not json')
    expect(formatToolPayload('"quoted"')).toBe('"quoted"')
  })

  it('omits empty arguments', () => {
    expect(formatToolArgs({})).toBe('')
    expect(formatToolArgs({ limit: 5 })).toBe('{\n  "limit": 5\n}')
  })
})
