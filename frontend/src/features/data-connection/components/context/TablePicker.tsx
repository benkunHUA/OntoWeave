import { AlertTriangle, KeyRound, RefreshCw, Search, SlidersHorizontal, Table2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type {
  SyncMode,
  SyncRun,
  SyncTask,
  SyncTaskSelection,
  TableSummary,
} from '../../../../shared/types'
import {
  ACTIVE_RUN_STATUSES,
  DEFAULT_SYNC_MODE,
  DEFAULT_VERSION_INTERVAL_SECONDS,
  VERSION_INTERVAL_OPTIONS,
} from '../../lib/constants'
import { formatRows } from '../../lib/format'
import { SYNC_MODE_LABELS } from '../../lib/labels'

interface TablePickerProps {
  tasks: SyncTask[]
  tables: TableSummary[]
  runs: SyncRun[]
  warnings: string[]
  isBusy: boolean
  onRefresh: () => Promise<void>
  onOpenSchema: (schema: string, table: string) => void
  onSave: (selections: SyncTaskSelection[], start: boolean) => Promise<void>
}

interface Draft {
  mode: SyncMode
  interval: number
}

type SelectionMap = Record<string, Draft>

function tableKey(schema: string, table: string): string {
  return `${schema}\u0000${table}`
}

function initialSelection(tasks: SyncTask[]): SelectionMap {
  const next: SelectionMap = {}
  for (const task of tasks) {
    if (task.status !== 'enabled') continue
    next[tableKey(task.schema, task.table)] = {
      mode: task.sync_mode,
      interval: task.version_interval_seconds,
    }
  }
  return next
}

export function TablePicker({
  tasks,
  tables,
  runs,
  warnings,
  isBusy,
  onRefresh,
  onOpenSchema,
  onSave,
}: TablePickerProps) {
  const [selection, setSelection] = useState<SelectionMap>(() => initialSelection(tasks))
  const [query, setQuery] = useState('')
  const [expanded, setExpanded] = useState<string | null>(null)
  const taskSignature = tasks.map((task) => `${task.id}:${task.updated_at}:${task.status}`).join('|')

  const activeRunByTask = useMemo(() => {
    const next = new Map<string, SyncRun>()
    for (const run of runs) {
      if (!ACTIVE_RUN_STATUSES.includes(run.status)) continue
      if (!next.has(run.sync_task_id)) next.set(run.sync_task_id, run)
    }
    return next
  }, [runs])

  const taskByTable = useMemo(() => {
    const next = new Map<string, SyncTask>()
    for (const task of tasks) next.set(tableKey(task.schema, task.table), task)
    return next
  }, [tasks])

  function activeRunFor(table: TableSummary): SyncRun | null {
    const task = taskByTable.get(tableKey(table.schema, table.name))
    return task ? activeRunByTask.get(task.id) ?? null : null
  }

  useEffect(() => {
    void Promise.resolve().then(() => setSelection(initialSelection(tasks)))
  }, [taskSignature]) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return tables
    return tables.filter((table) =>
      `${table.schema}.${table.name}`.toLowerCase().includes(keyword),
    )
  }, [query, tables])

  const selectedCount = Object.keys(selection).length
  const hasRunningSelection = tables.some(
    (table) => Boolean(selection[tableKey(table.schema, table.name)] && activeRunFor(table)),
  )

  function toggle(table: TableSummary) {
    if (!table.has_primary_key) return
    const key = tableKey(table.schema, table.name)
    setSelection((current) => {
      const next = { ...current }
      if (next[key]) {
        delete next[key]
      } else {
        next[key] = { mode: DEFAULT_SYNC_MODE, interval: DEFAULT_VERSION_INTERVAL_SECONDS }
      }
      return next
    })
  }

  function updateDraft(key: string, patch: Partial<Draft>) {
    setSelection((current) => ({ ...current, [key]: { ...current[key], ...patch } }))
  }

  async function submit(start: boolean) {
    if (start && hasRunningSelection) return
    const selections: SyncTaskSelection[] = tables
      .filter((table) => selection[tableKey(table.schema, table.name)])
      .map((table) => {
        const draft = selection[tableKey(table.schema, table.name)]
        return {
          schema: table.schema,
          table: table.name,
          sync_mode: draft.mode,
          version_interval_seconds: draft.mode === 'cdc' ? draft.interval : null,
        }
      })
    if (!selections.length) return
    await onSave(selections, start)
  }

  return (
    <div className="table-picker">
      <div className="table-toolbar">
        <label className="connection-search">
          <Search size={14} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索表名" />
        </label>
        <button type="button" className="icon-button" aria-label="刷新表列表" onClick={() => void onRefresh()}>
          <RefreshCw size={15} />
        </button>
      </div>

      {warnings.map((warning) => (
        <div key={warning} className="inline-alert">
          <AlertTriangle size={14} />
          <span>{warning}</span>
        </div>
      ))}

      <ul className="table-list">
        {filtered.map((table) => {
          const key = tableKey(table.schema, table.name)
          const draft = selection[key]
          const disabled = !table.has_primary_key
          const activeRun = activeRunFor(table)
          const running = Boolean(activeRun)
          return (
            <li
              key={key}
              className={`table-row ${draft ? 'is-selected' : ''} ${disabled ? 'is-disabled' : ''} ${running ? 'is-running' : ''}`}
            >
              <div className="table-row__main">
                <input
                  type="checkbox"
                  checked={Boolean(draft)}
                  disabled={disabled || running}
                  onChange={() => toggle(table)}
                  aria-label={`选择 ${table.schema}.${table.name}`}
                />
                <Table2 size={15} />
                <button type="button" className="table-row__name" onClick={() => onOpenSchema(table.schema, table.name)}>
                  {table.schema}.{table.name}
                </button>
                {running ? (
                  <span className="table-row__running">运行中</span>
                ) : table.has_primary_key ? (
                  <KeyRound size={12} />
                ) : (
                  <span className="table-row__warn">无主键</span>
                )}
                <span className="table-row__rows">{formatRows(table.estimated_rows)} 行</span>
              </div>
              {table.comment ? <p className="table-row__comment">{table.comment}</p> : null}
              {running ? (
                <p className="table-row__comment">正在同步中，请先在下方停止运行后再重新开始。</p>
              ) : disabled ? (
                <p className="table-row__comment">第一版仅支持有主键表</p>
              ) : null}
              {draft ? (
                <div className="table-row__settings">
                  <select
                    value={draft.mode}
                    disabled={running}
                    onChange={(event) => updateDraft(key, { mode: event.target.value as SyncMode })}
                    aria-label="同步方式"
                  >
                    <option value="cdc">{SYNC_MODE_LABELS.cdc}</option>
                    <option value="snapshot">{SYNC_MODE_LABELS.snapshot}</option>
                  </select>
                  {draft.mode === 'cdc' ? (
                    <select
                      value={draft.interval}
                      disabled={running}
                      onChange={(event) => updateDraft(key, { interval: Number(event.target.value) })}
                      aria-label="版本发布间隔"
                    >
                      {VERSION_INTERVAL_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  ) : null}
                  <button
                    type="button"
                    className="icon-button icon-button--quiet"
                    aria-label="高级设置"
                    disabled={running}
                    onClick={() => setExpanded(expanded === key ? null : key)}
                  >
                    <SlidersHorizontal size={14} />
                  </button>
                </div>
              ) : null}
              {expanded === key && draft ? (
                <p className="table-row__advanced">
                  主键：{tasks.find((task) => tableKey(task.schema, task.table) === key)?.primary_key_columns.join(', ') ?? '保存后确认'}
                  {draft.mode === 'cdc' ? ' · 版本按设定间隔发布，增量持续写入最新版本。' : ' · 仅执行一次历史数据同步。'}
                </p>
              ) : null}
            </li>
          )
        })}
      </ul>

      {!filtered.length ? <div className="empty-panel">没有可选的数据表</div> : null}

      <div className="context-footer">
        <span className="selection-count">
          {hasRunningSelection ? '选中的表正在同步，请先停止运行' : `已选 ${selectedCount} 张表`}
        </span>
        <button type="button" className="secondary-button" disabled={isBusy || selectedCount === 0} onClick={() => void submit(false)}>
          仅保存选择
        </button>
        <button
          type="button"
          className="primary-button"
          disabled={isBusy || selectedCount === 0 || hasRunningSelection}
          onClick={() => void submit(true)}
        >
          {isBusy ? '处理中…' : hasRunningSelection ? '请先停止运行中的表' : '保存并开始同步'}
        </button>
      </div>
    </div>
  )
}
