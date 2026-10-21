import { describe, expect, it } from 'vitest'
import type { ConversationEvent } from '../../../shared/types'
import { mergeTimeline, operationDatasets, operationStatus, operationTitle } from './operationTimeline'

function event(overrides: Partial<ConversationEvent>): ConversationEvent {
  return {
    id: crypto.randomUUID(),
    event_id: crypto.randomUUID(),
    operation_id: 'op-1',
    run_id: null,
    turn_id: null,
    event_type: 'sync.run.queued',
    summary: '事件',
    payload: {},
    created_at: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('operationTimeline', () => {
  it('同一 operation_id 的事件合并成一张卡片', () => {
    const items = mergeTimeline([], [
      event({ event_type: 'connection.updated' }),
      event({
        event_type: 'connection.tested',
        payload: { status: 'success' },
        created_at: '2026-01-01T00:00:01Z',
      }),
    ])

    expect(items).toHaveLength(1)
    expect(items[0].kind).toBe('operation')
    if (items[0].kind === 'operation') {
      expect(items[0].title).toBe('保存并测试连接')
      expect(items[0].status).toBe('succeeded')
      expect(items[0].events).toHaveLength(2)
    }
  })

  it('连接测试失败标记整个操作为失败', () => {
    const events = [
      event({ event_type: 'connection.updated' }),
      event({ event_type: 'connection.tested', payload: { status: 'failed' } }),
    ]
    expect(operationStatus(events)).toBe('failed')
  })

  it('保存表选择并开始同步显示为进行中', () => {
    const events = [
      event({ event_type: 'sync.tasks.saved' }),
      event({ event_type: 'sync.run.queued' }),
    ]
    expect(operationTitle(events)).toBe('保存并开始同步')
    expect(operationStatus(events)).toBe('running')
  })

  it('数据集版本事件单独成卡片，便于回看每次发布', () => {
    const items = mergeTimeline([], [
      event({ event_type: 'dataset.version.published' }),
      event({ event_type: 'sync.run.succeeded' }),
    ])
    expect(items.filter((item) => item.kind === 'event')).toHaveLength(1)
    expect(items.filter((item) => item.kind === 'operation')).toHaveLength(1)
  })

  it('消息与事件按时间排序', () => {
    const items = mergeTimeline(
      [{ id: 'm1', role: 'user', blocks: [], created_at: '2026-01-01T00:00:02Z' }],
      [event({ created_at: '2026-01-01T00:00:01Z' })],
    )
    expect(items.map((item) => item.kind)).toEqual(['operation', 'message'])
  })

  it('同一次操作按数据集汇总全部同步对象', () => {
    const datasets = operationDatasets([
      event({
        summary: 'demo.customers：已进入同步队列',
        payload: { datasetId: 'dataset-customers' },
      }),
      event({
        summary: 'demo.orders：已进入同步队列',
        payload: { datasetId: 'dataset-orders' },
        created_at: '2026-01-01T00:00:01Z',
      }),
      event({
        summary: 'demo.customers：Flink 作业已开始运行',
        payload: { datasetId: 'dataset-customers' },
        created_at: '2026-01-01T00:00:02Z',
      }),
    ])

    expect(datasets).toEqual([
      {
        datasetId: 'dataset-customers',
        label: 'demo.customers',
        summary: 'demo.customers：Flink 作业已开始运行',
        at: '2026-01-01T00:00:02Z',
      },
      {
        datasetId: 'dataset-orders',
        label: 'demo.orders',
        summary: 'demo.orders：已进入同步队列',
        at: '2026-01-01T00:00:01Z',
      },
    ])
  })
})
