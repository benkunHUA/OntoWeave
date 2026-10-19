import { Square } from 'lucide-react'
import type { SyncRun, SyncTask } from '../../../../shared/types'
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
    <div className="sync-task-list">
      <section className="context-section">
        <h2>同步任务</h2>
        {activeTasks.length ? (
          <ul className="compact-list">
            {activeTasks.map((task) => (
              <li key={task.id} className="sync-task-list__item">
                <span className="compact-list__main">{task.schema}.{task.table}</span>
                <span className="compact-list__meta">
                  {SYNC_MODE_LABELS[task.sync_mode]}
                  {task.sync_mode === 'cdc' ? ` · ${formatInterval(task.version_interval_seconds)}` : ''}
                </span>
                <span className={`status-pill is-${task.status}`}>{task.status === 'enabled' ? '已启用' : '已停用'}</span>
              </li>
            ))}
          </ul>
        ) : <div className="empty-panel">没有正在运行的同步任务</div>}
      </section>

      <section className="context-section">
        <h2>最近运行</h2>
        {runs.length ? (
          <ul className="compact-list">
            {runs.map((run) => {
              const active = ACTIVE_RUN_STATUSES.includes(run.status)
              return (
                <li key={run.id}>
                  <span className="compact-list__main">{RUN_STATUS_LABELS[run.status]}</span>
                  <span className="compact-list__meta">
                    {SYNC_MODE_LABELS[run.run_mode]} · {formatDateTime(run.created_at)}
                  </span>
                  {run.error_message ? (
                    <FailureDetails message={run.error_message} code={run.error_code} />
                  ) : null}
                  {active ? (
                    <button type="button" className="text-button" disabled={isBusy} onClick={() => onStop(run.id)}>
                      <Square size={12} />
                      停止
                    </button>
                  ) : null}
                </li>
              )
            })}
          </ul>
        ) : <div className="empty-panel">还没有同步运行</div>}
      </section>
    </div>
  )
}
