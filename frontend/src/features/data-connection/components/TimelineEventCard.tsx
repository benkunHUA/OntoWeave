import {
  Ban,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Database,
  LoaderCircle,
  MessageSquareText,
} from 'lucide-react'
import { useState } from 'react'
import type { ConversationEvent } from '../../../shared/types'
import type { OperationStatus } from '../lib/operationTimeline'
import { operationStepLabel } from '../lib/operationTimeline'

const STATUS_LABELS: Record<OperationStatus, string> = {
  running: '进行中',
  succeeded: '已完成',
  failed: '失败',
  stopped: '已停止',
}

function StatusIcon({ status }: { status: OperationStatus }) {
  if (status === 'running') return <LoaderCircle size={14} className="is-spinning" />
  if (status === 'failed') return <CircleAlert size={14} />
  if (status === 'stopped') return <Ban size={14} />
  return <CircleCheck size={14} />
}

function eventError(event: ConversationEvent): string | null {
  const message = event.payload.errorMessage
  return typeof message === 'string' && message ? message : null
}

function datasetIdOf(event: ConversationEvent): string | null {
  const value = event.payload.datasetId
  return typeof value === 'string' ? value : null
}

interface OperationCardProps {
  title: string
  status: OperationStatus
  events: ConversationEvent[]
  onExplain: (summary: string) => void
  onOpenDataset: (datasetId: string) => void
}

export function OperationCard({ title, status, events, onExplain, onOpenDataset }: OperationCardProps) {
  const [open, setOpen] = useState(false)
  const latest = events.at(-1)
  const failure = events.map(eventError).find(Boolean)
  const datasetId = events.map(datasetIdOf).find(Boolean) ?? null

  return (
    <article className={`operation-card is-${status}`}>
      <button type="button" className="operation-card__head" onClick={() => setOpen((value) => !value)}>
        <StatusIcon status={status} />
        <span className="operation-card__title">{title}</span>
        <span className="operation-card__status">{STATUS_LABELS[status]}</span>
        <ChevronRight size={13} className={open ? 'is-open' : ''} />
      </button>
      <p className="operation-card__summary">{failure ?? latest?.summary ?? title}</p>
      {open ? (
        <ol className="operation-card__steps">
          {events.map((event) => (
            <li key={event.id}>
              <span>{operationStepLabel(event)}</span>
              <time>{new Date(event.created_at).toLocaleTimeString()}</time>
            </li>
          ))}
        </ol>
      ) : null}
      {status === 'failed' || datasetId ? (
        <div className="operation-card__actions">
          {datasetId ? (
            <button type="button" onClick={() => onOpenDataset(datasetId)}>
              <Database size={13} />
              查看数据集
            </button>
          ) : null}
          {status === 'failed' ? (
            <button type="button" onClick={() => onExplain(failure ?? latest?.summary ?? title)}>
              <MessageSquareText size={13} />
              让 Agent 解释
            </button>
          ) : null}
        </div>
      ) : null}
    </article>
  )
}

interface EventCardProps {
  event: ConversationEvent
  onOpenDataset: (datasetId: string) => void
}

export function EventCard({ event, onOpenDataset }: EventCardProps) {
  const datasetId = datasetIdOf(event)
  return (
    <article className="operation-card is-event">
      <div className="operation-card__head">
        <Database size={14} />
        <span className="operation-card__title">{event.summary}</span>
      </div>
      {datasetId ? (
        <div className="operation-card__actions">
          <button type="button" onClick={() => onOpenDataset(datasetId)}>
            <Database size={13} />
            查看数据集
          </button>
        </div>
      ) : null}
    </article>
  )
}
