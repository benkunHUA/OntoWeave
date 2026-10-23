import { Ban, RefreshCw, X } from 'lucide-react'
import { useEscapeClose } from '../../../../shared/hooks/useEscapeClose'
import { RUN_STATUS_LABELS, isActiveRun } from '../../lib/constants'
import type { IndexRun } from '../../../../shared/types'
import { RUN_PHASE_LABELS, RUN_TRIGGER_LABELS, formatRunTime, runCounts, runSamples } from './runFormat'

interface RunDetailDrawerProps {
  run: IndexRun
  isLoading?: boolean
  error?: string | null
  onClose: () => void
  onCancel?: (run: IndexRun) => void
  onRetry?: (run: IndexRun) => void
}

const RUN_DETAIL_COPY = {
  title: '运行详情',
  close: '关闭',
  id: '运行 ID',
  status: '状态',
  trigger: '触发方式',
  phase: '当前阶段',
  sourceVersion: '来源数据集版本',
  revision: '映射 revision',
  requested: '入队时间',
  started: '开始时间',
  finished: '结束时间',
  written: '写入数',
  warnings: '告警数',
  skipped: '跳过数',
  error: '错误',
  samples: '行级样例',
  noSamples: '没有行级样例',
  cancel: '取消运行',
  retry: '重试生成',
  loading: '正在刷新运行详情…',
} as const

function fact(label: string, value: string | number) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

export function RunDetailDrawer({
  run,
  isLoading = false,
  error = null,
  onClose,
  onCancel,
  onRetry,
}: RunDetailDrawerProps) {
  useEscapeClose(true, onClose)
  const counts = runCounts(run)
  const samples = runSamples(run)

  return (
    <aside className="ontology-run-drawer" role="dialog" aria-modal="true" aria-label={RUN_DETAIL_COPY.title}>
      <header className="ontology-run-drawer__head">
        <div>
          <span className="eyebrow">{RUN_DETAIL_COPY.title}</span>
          <strong><code>{run.id}</code></strong>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label={RUN_DETAIL_COPY.close} onClick={onClose}>
          <X size={16} />
        </button>
      </header>

      <div className="ontology-run-drawer__body">
        {isLoading ? <p className="ontology-run-drawer__loading">{RUN_DETAIL_COPY.loading}</p> : null}
        {error ? <p className="ontology-detail-error" role="alert">{error}</p> : null}

        <dl className="ontology-run-facts">
          {fact(RUN_DETAIL_COPY.status, RUN_STATUS_LABELS[run.status] ?? run.status)}
          {fact(RUN_DETAIL_COPY.trigger, RUN_TRIGGER_LABELS[run.trigger_type] ?? run.trigger_type)}
          {fact(RUN_DETAIL_COPY.phase, run.phase ? RUN_PHASE_LABELS[run.phase] ?? run.phase : '—')}
          {fact(RUN_DETAIL_COPY.sourceVersion, run.target_dataset_version_id)}
          {fact(RUN_DETAIL_COPY.revision, run.mapping_revision)}
          {fact(RUN_DETAIL_COPY.requested, formatRunTime(run.requested_at))}
          {fact(RUN_DETAIL_COPY.started, formatRunTime(run.started_at))}
          {fact(RUN_DETAIL_COPY.finished, formatRunTime(run.finished_at))}
        </dl>

        <section className="ontology-run-counts" aria-label="运行计数">
          <div><span>{RUN_DETAIL_COPY.written}</span><strong>{counts.written}</strong></div>
          <div><span>{RUN_DETAIL_COPY.warnings}</span><strong>{counts.warnings}</strong></div>
          <div><span>{RUN_DETAIL_COPY.skipped}</span><strong>{counts.skipped}</strong></div>
        </section>

        {run.error_code || run.error_message ? (
          <section className="ontology-run-error" role="alert">
            <strong>{RUN_DETAIL_COPY.error}</strong>
            <code>{run.error_code || 'UNKNOWN'}</code>
            {run.error_message ? <p>{run.error_message}</p> : null}
          </section>
        ) : null}

        <section className="ontology-run-samples">
          <h3>{RUN_DETAIL_COPY.samples}</h3>
          {samples.length ? (
            <ol>{samples.map((sample, index) => <li key={`${index}-${sample}`}><code>{sample}</code></li>)}</ol>
          ) : <p>{RUN_DETAIL_COPY.noSamples}</p>}
        </section>
      </div>

      <footer className="ontology-run-drawer__foot">
        {run.status === 'FAILED' && onRetry ? (
          <button type="button" className="secondary-button" onClick={() => onRetry(run)}>
            <RefreshCw size={13} /> {RUN_DETAIL_COPY.retry}
          </button>
        ) : null}
        {isActiveRun(run) && onCancel ? (
          <button type="button" className="danger-button" onClick={() => onCancel(run)}>
            <Ban size={13} /> {RUN_DETAIL_COPY.cancel}
          </button>
        ) : null}
      </footer>
    </aside>
  )
}
