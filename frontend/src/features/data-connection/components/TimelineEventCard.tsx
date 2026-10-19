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
import { operationDatasets, operationStepLabel } from '../lib/operationTimeline'
import { FailureDetails } from './FailureDetails'

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

interface EventFailure {
  message: string
  code: string | null
}

function eventFailure(event: ConversationEvent): EventFailure | null {
  const message = event.payload?.errorMessage
  if (typeof message !== 'string' || !message) return null
  const code = event.payload?.errorCode
  return {
    message,
    code: typeof code === 'string' && code ? code : null,
  }
}

function datasetIdOf(event: ConversationEvent): string | null {
  const value = event.payload?.datasetId
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
  const failure = events
    .map(eventFailure)
    .filter((item): item is EventFailure => item !== null)
    .at(-1) ?? null
  const datasets = operationDatasets(events)

  return (
    <article className={`operation-card is-${status}`}>
      <button type="button" className="operation-card__head" onClick={() => setOpen((value) => !value)}>
        <StatusIcon status={status} />
        <span className="operation-card__title">{title}</span>
        <span className="operation-card__status">{STATUS_LABELS[status]}</span>
        <ChevronRight size={13} className={open ? 'is-open' : ''} />
      </button>
      {datasets.length ? (
        <ul className="operation-card__datasets">
          {datasets.map((dataset) => (
            <li key={dataset.datasetId}>{dataset.summary}</li>
          ))}
        </ul>
      ) : (
        <p className="operation-card__summary">{latest?.summary ?? title}</p>
      )}
      {failure ? <FailureDetails message={failure.message} code={failure.code} /> : null}
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
      {status === 'failed' || datasets.length ? (
        <div className="operation-card__actions">
          {datasets.map((dataset) => (
            <button key={dataset.datasetId} type="button" onClick={() => onOpenDataset(dataset.datasetId)}>
              <Database size={13} />
              {datasets.length === 1 ? '查看数据集' : `查看 ${dataset.label}`}
            </button>
          ))}
          {status === 'failed' ? (
            <button type="button" onClick={() => onExplain(failure?.message ?? latest?.summary ?? title)}>
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
