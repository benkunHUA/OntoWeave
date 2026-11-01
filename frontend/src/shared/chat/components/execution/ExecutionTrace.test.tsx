// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '../../../../test/render'
import type { MessageBlock } from '../../../types'
import { groupExecutionTraces } from '../../lib/executionTrace'
import { ExecutionTrace } from './ExecutionTrace'

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>
type ReasoningBlock = Extract<MessageBlock, { kind: 'reasoning' }>

const toolBlock: ToolBlock = {
  id: 'call-1',
  kind: 'tool',
  callId: 'call-1',
  name: 'query_objects',
  args: { object_type_id: 'type-1', q: '王建国', limit: 20 },
  status: 'ok',
  summary: '业主 · 搜索「王建国」，最多 20 条',
  result: '{"items":[{"id":1}],"total":1}',
  durationMs: 1200,
}

const reasoningBlock: ReasoningBlock = {
  id: 'reasoning-1',
  kind: 'reasoning',
  text: '先确定业主对象类型',
  done: true,
}

afterEach(cleanup)

describe('ExecutionTrace', () => {
  it('collapses a finished trace to one summary line', () => {
    const [trace] = groupExecutionTraces([reasoningBlock, toolBlock])
    render(<ExecutionTrace trace={trace} label="查询过程" />)

    expect(screen.getByRole('button', { name: /查询过程 · 1 步 · 1.2s/ })).toBeTruthy()
    expect(screen.queryByText('查询业主「王建国」→ 1 条')).toBeNull()
  })

  it('opens steps and shows JSON only in the detail dialog', () => {
    const [trace] = groupExecutionTraces([reasoningBlock, toolBlock])
    const { container } = render(<ExecutionTrace trace={trace} label="查询过程" />)

    fireEvent.click(screen.getByRole('button', { name: /查询过程/ }))
    expect(screen.queryByText('先确定业主对象类型')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: /查询业主「王建国」→ 1 条/ }))

    expect(screen.getByText('执行步骤详情')).toBeTruthy()
    const payloads = [...container.ownerDocument.querySelectorAll('pre')].map(
      (node) => node.textContent,
    )
    expect(payloads.some((value) => value?.includes('"q": "王建国"'))).toBe(true)
    expect(payloads.some((value) => value?.includes('"total": 1'))).toBe(true)
  })

  it('aggregates reasoning behind a folded row', () => {
    const [trace] = groupExecutionTraces([
      reasoningBlock,
      { ...reasoningBlock, id: 'reasoning-2', text: '再核对关系' },
      toolBlock,
    ])
    render(<ExecutionTrace trace={trace} label="查询过程" />)

    fireEvent.click(screen.getByRole('button', { name: /查询过程/ }))
    fireEvent.click(screen.getByRole('button', { name: /思考 2 段/ }))

    expect(screen.getByText('先确定业主对象类型')).toBeTruthy()
    expect(screen.getByText('再核对关系')).toBeTruthy()
  })

  it('keeps a running trace open and collapses it when finished', () => {
    const [running] = groupExecutionTraces([
      { ...toolBlock, status: 'running', result: null, durationMs: null },
    ])
    const [finished] = groupExecutionTraces([toolBlock])
    const { rerender } = render(<ExecutionTrace trace={running} label="查询过程" active />)

    expect(screen.getByText('查询业主「王建国」')).toBeTruthy()

    rerender(<ExecutionTrace trace={finished} label="查询过程" active={false} />)

    expect(screen.queryByText('查询业主「王建国」→ 1 条')).toBeNull()
  })

  it('keeps completed steps visible while the trace is running', () => {
    const [trace] = groupExecutionTraces([
      {
        ...toolBlock,
        id: 'call-0',
        callId: 'call-0',
        summary: '客户 · 搜索「张三」，最多 20 条',
        result: '{"items":[],"total":2}',
      },
      {
        ...toolBlock,
        id: 'call-1',
        callId: 'call-1',
        status: 'running',
        result: null,
        durationMs: null,
      },
    ])
    render(<ExecutionTrace trace={trace} label="查询过程" active />)

    expect(screen.getByText('查询业主「王建国」')).toBeTruthy()
    expect(screen.getByText('查询客户「张三」→ 2 条')).toBeTruthy()
  })

  it('does not render a persisted running step as live after the run has finished', () => {
    const [trace] = groupExecutionTraces([
      { ...toolBlock, status: 'running', result: null, durationMs: null },
    ])
    const { container } = render(<ExecutionTrace trace={trace} label="执行过程" />)

    expect(screen.queryByRole('button', { name: /执行中/ })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /执行过程 · 1 步/ }))
    expect(container.querySelector('.execution-step__status.is-spinning')).toBeNull()
  })

  it('stays open for the whole active run, even between tool status changes', () => {
    const [running] = groupExecutionTraces([
      { ...toolBlock, status: 'running', result: null, durationMs: null },
    ])
    const [finished] = groupExecutionTraces([toolBlock])
    const { rerender } = render(<ExecutionTrace trace={running} label="查询过程" active />)

    rerender(<ExecutionTrace trace={finished} label="查询过程" active />)

    expect(screen.getByText('查询业主「王建国」→ 1 条')).toBeTruthy()
    expect(screen.getByRole('button', { name: /查询过程 · 执行中 · 1 步/ })).toBeTruthy()

    rerender(<ExecutionTrace trace={finished} label="查询过程" active={false} />)

    expect(screen.queryByText('查询业主「王建国」→ 1 条')).toBeNull()
  })
})
