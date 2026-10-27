// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '../../../test/render'
import type { MessageBlock } from '../../types'
import { ToolCallBlock } from './ToolCallBlock'

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>

const block: ToolBlock = {
  id: 'call-1',
  kind: 'tool',
  callId: 'call-1',
  name: 'query_objects',
  args: { object_type_id: 'type-1', limit: 5 },
  status: 'ok',
  result: '{"items":[{"id":1,"name":"示例"}],"total":1}',
}

describe('ToolCallBlock', () => {
  afterEach(() => {
    cleanup()
  })

  it('shows a Chinese label instead of the machine name', () => {
    const { container } = render(<ToolCallBlock block={block} />)

    expect(screen.getByText('查询对象')).toBeTruthy()
    expect(container.textContent).not.toContain('query_objects')
  })

  it('shows the query summary after the tool label', () => {
    render(<ToolCallBlock block={{ ...block, summary: '客户 · 搜索「张三」' }} />)

    expect(screen.getByText('查询对象')).toBeTruthy()
    expect(screen.getByText('客户 · 搜索「张三」')).toBeTruthy()
  })

  it('pretty-prints JSON arguments and results', () => {
    const { container } = render(<ToolCallBlock block={block} />)
    fireEvent.click(screen.getByRole('button'))

    const payloads = [...container.querySelectorAll('pre')].map((node) => node.textContent)
    expect(payloads[0]).toContain('\n  "object_type_id": "type-1"')
    expect(payloads[1]).toContain('\n  "items": [')
    expect(payloads[1]).toContain('\n      "name": "示例"')
  })
})
