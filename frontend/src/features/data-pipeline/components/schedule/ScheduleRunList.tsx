import { ExternalLink } from 'lucide-react'
import type { PipelineScheduleRun } from '../../../../shared/types'
import { SCHEDULE_COPY } from '../../lib/executionConstants'
import { formatDateTime } from '../../lib/format'
import { scheduleRunLabel, scheduleRunStatusLabel } from '../../lib/schedule'

interface ScheduleRunListProps {
  runs: PipelineScheduleRun[]
  onOpenBuild: (buildId: string) => void
}

export function ScheduleRunList({ runs, onOpenBuild }: ScheduleRunListProps) {
  return (
    <section className="pipeline-schedule-runs">
      <h3>{SCHEDULE_COPY.history}</h3>
      {!runs.length ? <p className="pipeline-issue-empty">{SCHEDULE_COPY.emptyHistory}</p> : null}
      {runs.map((run) => (
        <article key={run.id}>
          <div>
            <strong>{scheduleRunLabel(run)}</strong>
            <small>{formatDateTime(run.scheduled_for)} · {scheduleRunStatusLabel(run.status)}</small>
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
