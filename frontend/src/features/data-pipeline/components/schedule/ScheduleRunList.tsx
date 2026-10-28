import { Button, Stack, Text } from '@mantine/core'
import { ExternalLink } from 'lucide-react'
import type { PipelineScheduleRun } from '../../../../shared/types'
import { SCHEDULE_COPY } from '../../lib/executionConstants'
import { formatDateTime } from '../../lib/format'
import {
  scheduleRunLabel,
  scheduleRunStatusLabel,
  scheduleRunTimingLabel,
  triggerTypeLabel,
} from '../../lib/schedule'

interface ScheduleRunListProps {
  runs: PipelineScheduleRun[]
  timeZone: string
  onOpenBuild: (buildId: string) => void
}

export function ScheduleRunList({ runs, timeZone, onOpenBuild }: ScheduleRunListProps) {
  return (
    <section className="pipeline-schedule-runs">
      <h3>{SCHEDULE_COPY.history}</h3>
      {!runs.length ? <Text className="pipeline-issue-empty" size="xs" c="dimmed">{SCHEDULE_COPY.emptyHistory}</Text> : null}
      {runs.map((run) => (
        <article key={run.id}>
          <Stack gap={2}>
            <Text component="strong" size="sm">{scheduleRunLabel(run)}</Text>
            <Text component="small" size="xs" c="dimmed">
              {formatDateTime(run.scheduled_for, timeZone)} · {timeZone} · {triggerTypeLabel(run.trigger_type)} · {scheduleRunStatusLabel(run.status)}
            </Text>
            {scheduleRunTimingLabel(run) ? (
              <Text component="small" size="xs" c="dimmed">{scheduleRunTimingLabel(run)}</Text>
            ) : null}
          </Stack>
          {run.build_id ? (
            <Button
              type="button"
              variant="subtle"
              size="compact-xs"
              rightSection={<ExternalLink size={12} />}
              onClick={() => onOpenBuild(run.build_id!)}
            >
              {SCHEDULE_COPY.buildLink}
            </Button>
          ) : null}
        </article>
      ))}
    </section>
  )
}
