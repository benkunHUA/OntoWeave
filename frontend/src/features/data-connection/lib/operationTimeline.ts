import type { ChatMessage, ConversationEvent } from '../../../shared/types'
import { EVENT_TYPE_LABELS, UNKNOWN_EVENT_LABEL } from './labels'

export type OperationStatus = 'running' | 'succeeded' | 'failed' | 'stopped'

export interface TimelineMessageItem {
  kind: 'message'
  at: string
  message: ChatMessage
}

export interface TimelineEventItem {
  kind: 'event'
  at: string
  event: ConversationEvent
}

export interface TimelineOperationItem {
  kind: 'operation'
  at: string
  operationId: string
  events: ConversationEvent[]
  title: string
  status: OperationStatus
}

export type TimelineItem = TimelineMessageItem | TimelineEventItem | TimelineOperationItem

export interface OperationDatasetSummary {
  datasetId: string
  label: string
  summary: string
  at: string
}

const RUNNING_EVENTS = new Set([
  'sync.run.queued',
  'sync.run.submitted',
  'sync.run.running',
  'sync.run.stopping',
])

const FAILED_EVENTS = new Set(['sync.run.failed'])

function timeOf(value: string): number {
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

function isStandalone(event: ConversationEvent): boolean {
  return !event.operation_id || event.event_type === 'dataset.version.published'
}

function eventLabel(event: ConversationEvent): string {
  return EVENT_TYPE_LABELS[event.event_type] ?? UNKNOWN_EVENT_LABEL
}

function datasetIdOf(event: ConversationEvent): string | null {
  const value = event.payload?.datasetId
  return typeof value === 'string' && value ? value : null
}

function datasetLabel(event: ConversationEvent): string {
  const name = event.payload?.datasetName
  if (typeof name === 'string' && name) return name
  const separator = event.summary.indexOf('：')
  return separator > 0 ? event.summary.slice(0, separator) : '数据集'
}

export function operationDatasets(events: ConversationEvent[]): OperationDatasetSummary[] {
  const latest = new Map<string, OperationDatasetSummary>()
  for (const event of events) {
    const datasetId = datasetIdOf(event)
    if (!datasetId) continue
    const current = latest.get(datasetId)
    if (current && timeOf(current.at) > timeOf(event.created_at)) continue
    latest.set(datasetId, {
      datasetId,
      label: datasetLabel(event),
      summary: event.summary,
      at: event.created_at,
    })
  }
  return [...latest.values()]
}

export function operationTitle(events: ConversationEvent[]): string {
  const types = new Set(events.map((event) => event.event_type))
  if (types.has('connection.updated') && types.has('connection.tested')) {
    return '保存并测试连接'
  }
  if (types.has('sync.tasks.saved') && [...types].some((type) => type.startsWith('sync.run.'))) {
    return '保存并开始同步'
  }
  if (types.has('sync.run.stopping') || types.has('sync.run.stopped')) return '停止同步'
  if (types.has('connection.tested')) return '测试连接'
  if (types.has('connection.updated')) return '保存连接配置'
  if (types.has('sync.tasks.saved')) return '保存表选择'
  if (types.has('sync.run.queued') || types.has('sync.run.submitted')) return '开始同步'
  return '数据操作'
}

export function operationStatus(events: ConversationEvent[]): OperationStatus {
  if (events.some((event) => FAILED_EVENTS.has(event.event_type))) return 'failed'
  if (events.some((event) => event.event_type === 'connection.tested')) {
    const tested = events.find((event) => event.event_type === 'connection.tested')
    return tested?.payload.status === 'failed' ? 'failed' : 'succeeded'
  }
  if (events.some((event) => event.event_type === 'sync.run.stopped')) return 'stopped'
  if (events.some((event) => event.event_type === 'sync.run.succeeded')) return 'succeeded'
  if (events.some((event) => RUNNING_EVENTS.has(event.event_type))) {
    return 'running'
  }
  if (events.some((event) => event.event_type === 'connection.updated')) return 'running'
  return 'succeeded'
}

export function mergeTimeline(
  messages: ChatMessage[],
  events: ConversationEvent[],
): TimelineItem[] {
  const items: TimelineItem[] = []
  const groups = new Map<string, ConversationEvent[]>()

  for (const event of events) {
    if (isStandalone(event)) {
      items.push({ kind: 'event', at: event.created_at, event })
      continue
    }
    const operationId = event.operation_id as string
    const group = groups.get(operationId)
    if (group) {
      group.push(event)
    } else {
      groups.set(operationId, [event])
    }
  }

  for (const [operationId, group] of groups) {
    const ordered = [...group].sort((left, right) => timeOf(left.created_at) - timeOf(right.created_at))
    items.push({
      kind: 'operation',
      at: ordered[0].created_at,
      operationId,
      events: ordered,
      title: operationTitle(ordered),
      status: operationStatus(ordered),
    })
  }

  for (const message of messages) {
    items.push({
      kind: 'message',
      at: message.created_at ?? new Date(0).toISOString(),
      message,
    })
  }

  return items.sort((left, right) => timeOf(left.at) - timeOf(right.at))
}

export function eventStepLabel(event: ConversationEvent): string {
  return eventLabel(event)
}

export function operationStepLabel(event: ConversationEvent): string {
  return EVENT_TYPE_LABELS[event.event_type] ?? event.summary
}
