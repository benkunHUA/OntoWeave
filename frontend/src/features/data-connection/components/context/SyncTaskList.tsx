import { Badge, Button, Group, Stack, Text, Title } from '@mantine/core'
import { Square } from 'lucide-react'
import type { SyncRun, SyncTask } from '../../../../shared/types'
import { EmptyState } from '../../../../shared/components/EmptyState'
import { formatDateTime, formatInterval } from '../../lib/format'
import { ACTIVE_RUN_STATUSES } from '../../lib/constants'
import { RUN_STATUS_LABELS, SYNC_MODE_LABELS } from '../../lib/labels'
import { FailureDetails } from '../FailureDetails'

interface SyncTaskListProps {
  tasks: SyncTask[]
  runs: SyncRun[]
  isBusy: boolean
  onStop: (runId: string) => void
}

export function SyncTaskList({ tasks, runs, isBusy, onStop }: SyncTaskListProps) {
  const activeTaskIds = new Set(runs.filter((run) => ACTIVE_RUN_STATUSES.includes(run.status)).map((run) => run.sync_task_id))
  const activeTasks = tasks.filter((task) => activeTaskIds.has(task.id))

  return (
    <Stack className="sync-task-list" gap="lg">
      <section className="context-section">
        <Group justify="space-between"><Title order={3}>同步任务</Title><Badge color="gray">{activeTasks.length}</Badge></Group>
        {activeTasks.length ? (
          <ul className="compact-list">
            {activeTasks.map((task) => (
              <li key={task.id}>
                <Text component="span" className="compact-list__main" size="sm">{task.schema}.{task.table}</Text>
                <Text component="span" className="compact-list__meta" size="xs" c="dimmed">
                  {SYNC_MODE_LABELS[task.sync_mode]}
                  {task.sync_mode === 'cdc' ? ` · ${formatInterval(task.version_interval_seconds)}` : ''}
                </Text>
                <Badge color={task.status === 'enabled' ? 'teal' : 'gray'} variant="light">
                  {task.status === 'enabled' ? '已启用' : '已停用'}
                </Badge>
              </li>
            ))}
          </ul>
        ) : <EmptyState title="没有正在运行的同步任务" />}
      </section>

      <section className="context-section">
        <Group justify="space-between"><Title order={3}>最近运行</Title><Badge color="gray">{runs.length}</Badge></Group>
        {runs.length ? (
          <ul className="compact-list">
            {runs.map((run) => {
              const active = ACTIVE_RUN_STATUSES.includes(run.status)
              return (
                <li key={run.id}>
                  <Text component="span" className="compact-list__main" size="sm">{RUN_STATUS_LABELS[run.status]}</Text>
                  <Text component="span" className="compact-list__meta" size="xs" c="dimmed">
                    {SYNC_MODE_LABELS[run.run_mode]} · {formatDateTime(run.created_at)}
                  </Text>
                  {run.error_message ? (
                    <FailureDetails message={run.error_message} code={run.error_code} />
                  ) : null}
                  {active ? (
                    <Button
                      type="button"
                      variant="subtle"
                      color="red"
                      size="compact-xs"
                      leftSection={<Square size={12} />}
                      disabled={isBusy}
                      onClick={() => onStop(run.id)}
                    >
                      停止
                    </Button>
                  ) : null}
                </li>
              )
            })}
          </ul>
        ) : <EmptyState title="还没有同步运行" />}
      </section>
    </Stack>
  )
}
