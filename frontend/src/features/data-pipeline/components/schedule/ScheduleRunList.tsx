import { ExternalLink } from 'lucide-react'
import type { PipelineScheduleRun } from '../../../../shared/types'
import { SCHEDULE_COPY } from '../../lib/executionConstants'
import { formatDateTime, formatDuration } from '../../lib/format'
import {
  scheduleRunLabel,
  scheduleRunStatusLabel,
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
      {!runs.length ? <p className="pipeline-issue-empty">{SCHEDULE_COPY.emptyHistory}</p> : null}
      {runs.map((run) => (
        <article key={run.id}>
          <div>
            <strong>{scheduleRunLabel(run)}</strong>
            <small>
              {formatDateTime(run.scheduled_for, timeZone)} · {timeZone} · {triggerTypeLabel(run.trigger_type)} · {scheduleRunStatusLabel(run.status)}
            </small>
            {run.finished_at ? (
              <small>耗时 {formatDuration(run.started_at ?? run.scheduled_for, run.finished_at)}</small>
            ) : null}
          </div>
          {run.build_id ? (
            <button type="button" className="text-button" onClick={() => onOpenBuild(run.build_id!)}>
              {SCHEDULE_COPY.buildLink}
              <ExternalLink size={12} />
            </button>
          ) : null}
        </article>
      ))}
    </section>
  )
}
