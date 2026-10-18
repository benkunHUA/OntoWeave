import { ArrowRight, CircleAlert, CircleCheck, PlugZap } from 'lucide-react'
import type { DataConnectionState, MissingStep } from '../../../../shared/types'
import { formatDateTime } from '../../lib/format'
import { MISSING_STEP_LABELS, RUN_STATUS_LABELS, TEST_STATUS_LABELS } from '../../lib/labels'
import type { ContextTab } from '../../lib/constants'

interface ConnectionOverviewProps {
  state: DataConnectionState
  isBusy: boolean
  onEdit: () => void
  onTest: () => void
  onOpenTab: (tab: ContextTab) => void
}

function nextStep(state: DataConnectionState): MissingStep | null {
  return (state.missing_steps[0] as MissingStep | undefined) ?? null
}

export function ConnectionOverview({ state, isBusy, onEdit, onTest, onOpenTab }: ConnectionOverviewProps) {
  const { connection, tasks, recent_runs: runs, datasets } = state
  const step = nextStep(state)
  const latestRun = runs[0]

  function runNextStep() {
    if (!step) return
    if (step === 'SAVE_CONNECTION') return onEdit()
    if (step === 'TEST_CONNECTION') return onTest()
    onOpenTab('tables')
  }

  return (
    <div className="connection-overview">
      <section className="context-section">
        <h2>连接信息</h2>
        <dl className="detail-grid">
          <div><dt>类型</dt><dd>{connection.kind.toUpperCase()}</dd></div>
          <div><dt>主机</dt><dd>{connection.config.host ?? '—'} : {connection.config.port ?? '—'}</dd></div>
          <div><dt>数据库</dt><dd>{connection.config.database ?? '—'}</dd></div>
          <div><dt>用户名</dt><dd>{connection.config.username ?? '—'}</dd></div>
          <div><dt>加密连接</dt><dd>{connection.config.useSsl ? '开启' : '关闭'}</dd></div>
          <div><dt>最近更新</dt><dd>{formatDateTime(connection.updated_at)}</dd></div>
        </dl>
      </section>

      <section className="context-section">
        <h2>测试结果</h2>
        {connection.last_test_status === 'success' ? (
          <div className="inline-alert is-success">
            <CircleCheck size={15} />
            <span>
              {TEST_STATUS_LABELS.success}
              {connection.last_test_details?.serverVersion
                ? ` · MySQL ${String(connection.last_test_details.serverVersion)}`
                : ''}
              {connection.last_test_details?.latencyMs
                ? ` · ${String(connection.last_test_details.latencyMs)}ms`
                : ''}
            </span>
          </div>
        ) : connection.last_test_status === 'failed' ? (
          <div className="inline-alert is-error">
            <CircleAlert size={15} />
            <span>{connection.last_test_error ?? TEST_STATUS_LABELS.failed}</span>
          </div>
        ) : (
          <div className="inline-alert">
            <PlugZap size={15} />
            <span>{TEST_STATUS_LABELS.untested}</span>
          </div>
        )}
      </section>

      <section className="context-section">
        <h2>同步概况</h2>
        <dl className="detail-grid">
          <div><dt>已选表</dt><dd>{tasks.filter((task) => task.status === 'enabled').length}</dd></div>
          <div><dt>数据集</dt><dd>{datasets.length}</dd></div>
          <div><dt>最近运行</dt><dd>{latestRun ? RUN_STATUS_LABELS[latestRun.status] : '暂无'}</dd></div>
          <div><dt>最近更新</dt><dd>{formatDateTime(latestRun?.created_at)}</dd></div>
        </dl>
      </section>

      {step ? (
        <section className="next-step-card">
          <div>
            <span className="eyebrow">下一步</span>
            <strong>{MISSING_STEP_LABELS[step]}</strong>
          </div>
          <button type="button" className="primary-button" disabled={isBusy} onClick={runNextStep}>
            {MISSING_STEP_LABELS[step]}
            <ArrowRight size={15} />
          </button>
        </section>
      ) : null}
    </div>
  )
}
