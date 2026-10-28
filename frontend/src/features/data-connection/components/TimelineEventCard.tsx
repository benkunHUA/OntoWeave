import {
  Badge,
  Button,
  Card,
  Group,
  Stack,
  Text,
  ThemeIcon,
  UnstyledButton,
} from '@mantine/core'
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

const STATUS_COLORS: Record<OperationStatus, string> = {
  running: 'blue',
  succeeded: 'teal',
  failed: 'red',
  stopped: 'gray',
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
    <Card className={`operation-card is-${status}`} padding="sm" radius="md">
      <UnstyledButton
        type="button"
        className="operation-card__head"
        onClick={() => setOpen((value) => !value)}
        w="100%"
      >
        <Group justify="space-between" gap="sm" wrap="nowrap">
          <Group gap={8} wrap="nowrap">
            <ThemeIcon size="sm" variant="light" color={STATUS_COLORS[status]} aria-hidden="true">
              <StatusIcon status={status} />
            </ThemeIcon>
            <Text className="operation-card__title" size="sm" fw={600}>{title}</Text>
          </Group>
          <Group gap={6} wrap="nowrap">
            <Badge color={STATUS_COLORS[status]} variant="light">{STATUS_LABELS[status]}</Badge>
            <ChevronRight size={13} className={open ? 'is-open' : ''} />
          </Group>
        </Group>
      </UnstyledButton>
      {datasets.length ? (
        <Stack className="operation-card__datasets" gap={3} mt="xs">
          {datasets.map((dataset) => (
            <Text component="p" key={dataset.datasetId} size="xs" c="dimmed">{dataset.summary}</Text>
          ))}
        </Stack>
      ) : (
        <Text className="operation-card__summary" size="xs" c="dimmed" mt="xs">{latest?.summary ?? title}</Text>
      )}
      {failure ? <FailureDetails message={failure.message} code={failure.code} /> : null}
      {open ? (
        <Stack className="operation-card__steps" gap={4} mt="xs">
          {events.map((event) => (
            <Group key={event.id} justify="space-between" gap="sm">
              <Text component="span" size="xs">{operationStepLabel(event)}</Text>
              <Text component="time" size="xs" c="dimmed">{new Date(event.created_at).toLocaleTimeString()}</Text>
            </Group>
          ))}
        </Stack>
      ) : null}
      {status === 'failed' || datasets.length ? (
        <Group className="operation-card__actions" gap="xs" mt="xs">
          {datasets.map((dataset) => (
            <Button
              key={dataset.datasetId}
              type="button"
              variant="subtle"
              size="xs"
              leftSection={<Database size={13} />}
              onClick={() => onOpenDataset(dataset.datasetId)}
            >
              {datasets.length === 1 ? '查看数据集' : `查看 ${dataset.label}`}
            </Button>
          ))}
          {status === 'failed' ? (
            <Button
              type="button"
              variant="subtle"
              size="xs"
              leftSection={<MessageSquareText size={13} />}
              onClick={() => onExplain(failure?.message ?? latest?.summary ?? title)}
            >
              让 Agent 解释
            </Button>
          ) : null}
        </Group>
      ) : null}
    </Card>
  )
}

interface EventCardProps {
  event: ConversationEvent
  onOpenDataset: (datasetId: string) => void
}

export function EventCard({ event, onOpenDataset }: EventCardProps) {
  const datasetId = datasetIdOf(event)
  return (
    <Card className="operation-card is-event" padding="sm" radius="md">
      <Group className="operation-card__head" gap={8}>
        <ThemeIcon size="sm" variant="light" color="gray" aria-hidden="true">
          <Database size={14} />
        </ThemeIcon>
        <Text className="operation-card__title" size="sm" fw={600}>{event.summary}</Text>
      </Group>
      {datasetId ? (
        <Group className="operation-card__actions" gap="xs" mt="xs">
          <Button
            type="button"
            variant="subtle"
            size="xs"
            leftSection={<Database size={13} />}
            onClick={() => onOpenDataset(datasetId)}
          >
            查看数据集
          </Button>
        </Group>
      ) : null}
    </Card>
  )
}
