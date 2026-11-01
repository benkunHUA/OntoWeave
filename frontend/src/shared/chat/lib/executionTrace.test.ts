import { describe, expect, it } from 'vitest'
import type { MessageBlock } from '../../../shared/types'
import {
  describeExecutionStep,
  executionTraceHeading,
  formatExecutionDuration,
  groupExecutionTraces,
  orderMessageBlocks,
  toolResultCount,
} from './executionTrace'

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>
type ReasoningBlock = Extract<MessageBlock, { kind: 'reasoning' }>

function tool(id: string, overrides: Partial<ToolBlock> = {}): ToolBlock {
  return {
    id,
    kind: 'tool',
    callId: id,
    name: 'query_objects',
    args: {},
    status: 'ok',
    result: null,
    durationMs: 600,
    ...overrides,
  }
}

function reasoning(id: string, text = '先查对象'): ReasoningBlock {
  return { id, kind: 'reasoning', text, done: true }
}

describe('groupExecutionTraces', () => {
  it('groups contiguous reasoning and tools into one trace', () => {
    const traces = groupExecutionTraces([
      reasoning('r1'),
      tool('t1'),
      tool('t2', { durationMs: 900 }),
      { id: 'text-1', kind: 'text', text: '答案' },
      tool('t3'),
    ])

    expect(traces).toHaveLength(2)
    expect(traces[0]).toMatchObject({ stepCount: 2, durationMs: 1500, running: false })
    expect(traces[0].reasoning).toHaveLength(1)
    expect(traces[1].tools.map((item) => item.id)).toEqual(['t3'])
  })

  it('marks running and failed traces', () => {
    const [trace] = groupExecutionTraces([
      tool('t1', { status: 'running' }),
      tool('t2', { status: 'error' }),
    ])

    expect(trace.running).toBe(true)
    expect(trace.failed).toBe(true)
  })

  it('keeps a reasoning-only trace live until the reasoning ends', () => {
    const [trace] = groupExecutionTraces([
      { id: 'r1', kind: 'reasoning', text: '正在想', done: false },
    ])

    expect(trace.running).toBe(true)
  })
})

describe('orderMessageBlocks', () => {
  it('places execution before answer text and interrupts', () => {
    const groups = orderMessageBlocks([
      { id: 'text-1', kind: 'text', text: '最终答案' },
      tool('t1'),
      { id: 'interrupt-1', kind: 'interrupt', question: '继续吗', options: [], freeText: null },
      { id: 'notice-1', kind: 'notice', text: '已保存' },
    ])

    expect(groups.traces).toHaveLength(1)
    expect(groups.text.map((block) => block.id)).toEqual(['text-1'])
    expect(groups.interrupts.map((block) => block.id)).toEqual(['interrupt-1'])
    expect(groups.notices.map((block) => block.id)).toEqual(['notice-1'])
  })
})

describe('execute step summaries', () => {
  it('reads the total result count from JSON payloads', () => {
    expect(toolResultCount('{"items":[{"id":1}],"total":8}')).toBe(8)
    expect(toolResultCount('[{"id":1},{"id":2}]')).toBe(2)
    expect(toolResultCount('命中 3 条')).toBe(3)
  })

  it('turns a search summary into object, condition and result count', () => {
    expect(
      describeExecutionStep(
        tool('t1', {
          summary: '业主 · 搜索「王建国」，最多 20 条',
          result: '{"items":[{"id":1}],"total":1}',
        }),
      ),
    ).toBe('查询业主「王建国」→ 1 条')
  })

  it('describes filters, related objects and graph details', () => {
    expect(
      describeExecutionStep(
        tool('t1', {
          summary: '客户 · 按 2 个条件筛选，最多 20 条',
          result: '{"items":[],"total":5}',
        }),
      ),
    ).toBe('查询客户 · 2 个条件 → 5 条')

    expect(
      describeExecutionStep(
        tool('t2', {
          name: 'get_related_objects',
          summary: '从「客户」沿「拥有」查「订单」，3 个起点，最多 50 条',
          result: '{"items":[],"total":8}',
        }),
      ),
    ).toBe('从「客户」沿「拥有」查「订单」，3 个起点 → 8 条')

    expect(
      describeExecutionStep(
        tool('t3', {
          name: 'get_graph_detail',
          result: '{"name":"燃气业务图","object_types":[{},{}],"relations":[{}]}',
        }),
      ),
    ).toBe('读取业务图「燃气业务图」 · 2 类对象 / 1 条关系')
  })
})

describe('execution trace presentation', () => {
  it('formats durations for the collapsed heading', () => {
    expect(formatExecutionDuration(3600)).toBe('3.6s')
    expect(formatExecutionDuration(900)).toBe('900ms')

    const [trace] = groupExecutionTraces([
      tool('t1', { durationMs: 600 }),
      tool('t2', { durationMs: 1800 }),
      tool('t3', { durationMs: 1200 }),
    ])
    expect(executionTraceHeading(trace, '查询过程')).toBe('查询过程 · 3 步 · 3.6s')
  })

  it('uses a running heading before results arrive', () => {
    const [trace] = groupExecutionTraces([tool('t1', { status: 'running', durationMs: null })])
    expect(executionTraceHeading(trace, '查询过程')).toBe('查询过程 · 执行中 · 1 步')
  })
})
