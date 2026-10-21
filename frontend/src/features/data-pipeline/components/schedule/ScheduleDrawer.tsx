import { CalendarClock, LoaderCircle, Pause, Play, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { Pipeline } from '../../../../shared/types'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import { usePipelineSchedule } from '../../hooks/usePipelineSchedule'
import { SCHEDULE_COPY } from '../../lib/executionConstants'
import { formatDateTime } from '../../lib/format'
import {
  SCHEDULE_PRESETS,
  SCHEDULE_TIMEZONES,
  scheduleStatusLabel,
  scheduleVersionHint,
} from '../../lib/schedule'
import { ScheduleRunList } from './ScheduleRunList'

interface ScheduleDrawerProps {
  open: boolean
  pipeline: Pipeline | null
  hasDraftChanges: boolean
  onClose: () => void
  onOpenBuild: (buildId: string) => void
}

type ScheduleAction = 'save' | 'pause' | 'resume'

interface ScheduleForm {
  name: string
  cron: string
  timezone: string
}

const DEFAULT_CRON = '0 9 * * *'
const DEFAULT_TIMEZONE = 'Asia/Shanghai'

function defaultForm(pipeline: Pipeline | null): ScheduleForm {
  return {
    name: pipeline ? `${pipeline.name} 定时调度` : '定时调度',
    cron: DEFAULT_CRON,
    timezone: DEFAULT_TIMEZONE,
  }
}

export function ScheduleDrawer({
  open,
  pipeline,
  hasDraftChanges,
  onClose,
  onOpenBuild,
}: ScheduleDrawerProps) {
  const schedules = usePipelineSchedule(pipeline?.id)
  const [form, setForm] = useState<ScheduleForm>(() => defaultForm(pipeline))
  const [action, setAction] = useState<ScheduleAction | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    const current = schedules.schedule
    setForm(current ? {
      name: current.name,
      cron: current.cron_expression,
      timezone: current.timezone,
    } : defaultForm(pipeline))
  }, [pipeline, schedules.schedule])

  const preset = useMemo(
    () => SCHEDULE_PRESETS.find((item) => item.cron === form.cron)?.id ?? 'custom',
    [form.cron],
  )
  const hint = scheduleVersionHint(pipeline, hasDraftChanges)
  const timezones = useMemo(() => {
    const values = new Set<string>(SCHEDULE_TIMEZONES)
    if (form.timezone) values.add(form.timezone)
    return [...values]
  }, [form.timezone])

  if (!open) return null

  function update(patch: Partial<ScheduleForm>) {
    setForm((current) => ({ ...current, ...patch }))
  }

  async function confirmAction() {
    const pending = action
    if (!pending) return
    setSubmitting(true)
    setActionError(null)
    try {
      if (pending === 'pause') await schedules.pause()
      else if (pending === 'resume') await schedules.resume()
      else await schedules.put({
        name: form.name.trim(),
        cron_expression: form.cron.trim(),
        timezone: form.timezone,
      })
      setAction(null)
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : '调度操作失败')
      setAction(null)
    } finally {
      setSubmitting(false)
    }
  }

  const paused = schedules.schedule?.status === 'paused'
  const dialogCopy = action === 'pause'
    ? { title: SCHEDULE_COPY.pauseTitle, body: SCHEDULE_COPY.pauseBody, confirm: SCHEDULE_COPY.confirmPause }
    : action === 'resume'
      ? { title: SCHEDULE_COPY.resumeTitle, body: SCHEDULE_COPY.resumeBody, confirm: SCHEDULE_COPY.confirmResume }
      : schedules.missing
        ? { title: SCHEDULE_COPY.createTitle, body: SCHEDULE_COPY.createBody, confirm: SCHEDULE_COPY.confirmCreate }
        : { title: SCHEDULE_COPY.saveTitle, body: SCHEDULE_COPY.saveBody, confirm: SCHEDULE_COPY.confirmSave }

  return (
    <aside className="pipeline-drawer pipeline-schedule-drawer" aria-label={SCHEDULE_COPY.title}>
      <header className="pipeline-drawer__head">
        <div>
          <span className="eyebrow">Schedule</span>
          <h2>{SCHEDULE_COPY.title}</h2>
          <small>{SCHEDULE_COPY.subtitle}</small>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label="关闭" onClick={onClose}>
          <X size={16} />
        </button>
      </header>

      <div className="pipeline-drawer__body">
        {schedules.isLoading && !schedules.schedule ? (
          <p className="pipeline-preview-loading"><LoaderCircle size={16} />读取调度中…</p>
        ) : null}
        {schedules.error ? <p className="pipeline-alert is-error">{schedules.error}</p> : null}
        {actionError ? <p className="pipeline-alert is-error">{actionError}</p> : null}
        {hint ? <p className="pipeline-alert">{hint}</p> : null}

        <section className="pipeline-schedule-state">
          <div>
            <strong>{schedules.missing ? SCHEDULE_COPY.missing : scheduleStatusLabel(schedules.schedule?.status)}</strong>
            <span>{schedules.schedule?.trigger_type ?? 'schedule'}</span>
          </div>
          <small>{SCHEDULE_COPY.nextRun}：{formatDateTime(schedules.schedule?.next_run_at)}</small>
          <small>{SCHEDULE_COPY.lastRun}：{formatDateTime(schedules.schedule?.last_run_at)}</small>
        </section>

        <label className="pipeline-config-field">
          <span>{SCHEDULE_COPY.name}</span>
          <input value={form.name} onChange={(event) => update({ name: event.target.value })} />
        </label>
        <label className="pipeline-config-field">
          <span>{SCHEDULE_COPY.frequency}</span>
          <select
            value={preset}
            onChange={(event) => {
              const selected = SCHEDULE_PRESETS.find((item) => item.id === event.target.value)
              if (selected) update({ cron: selected.cron })
            }}
          >
            {SCHEDULE_PRESETS.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
            <option value="custom">自定义 cron</option>
          </select>
        </label>
        <label className="pipeline-config-field">
          <span>{SCHEDULE_COPY.customCron}</span>
          <input value={form.cron} onChange={(event) => update({ cron: event.target.value })} />
        </label>
        <label className="pipeline-config-field">
          <span>{SCHEDULE_COPY.timezone}</span>
          <select value={form.timezone} onChange={(event) => update({ timezone: event.target.value })}>
            {timezones.map((timezone) => <option key={timezone} value={timezone}>{timezone}</option>)}
          </select>
        </label>

        <ScheduleRunList runs={schedules.runs} onOpenBuild={onOpenBuild} />
      </div>

      <footer className="pipeline-drawer__foot pipeline-schedule-actions">
        <button
          type="button"
          className="primary-button"
          disabled={!form.name.trim() || !form.cron.trim() || schedules.isLoading}
          onClick={() => setAction('save')}
        >
          <CalendarClock size={14} />
          {schedules.missing ? SCHEDULE_COPY.create : SCHEDULE_COPY.save}
        </button>
        {!schedules.missing ? (
          <button
            type="button"
            className="secondary-button"
            disabled={schedules.isLoading}
            onClick={() => setAction(paused ? 'resume' : 'pause')}
          >
            {paused ? <Play size={14} /> : <Pause size={14} />}
            {paused ? SCHEDULE_COPY.resume : SCHEDULE_COPY.pause}
          </button>
        ) : null}
      </footer>

      {action ? (
        <DialogShell
          icon={<CalendarClock size={20} />}
          title={dialogCopy.title}
          subtitle={SCHEDULE_COPY.subtitle}
          onClose={() => setAction(null)}
          footer={<>
            <button type="button" className="secondary-button" onClick={() => setAction(null)}>
              取消
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={submitting}
              onClick={() => void confirmAction()}
            >
              {submitting ? <LoaderCircle size={14} /> : <CalendarClock size={14} />}
              {dialogCopy.confirm}
            </button>
          </>}
        >
          <div className="view-dialog__body">
            <p>{dialogCopy.body}</p>
            {action === 'save' ? (
              <p><code>{form.cron}</code> · {form.timezone}</p>
            ) : null}
          </div>
        </DialogShell>
      ) : null}
    </aside>
  )
}
