import { Alert, Button, Group, Paper, Text } from '@mantine/core'
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
          <Alert color="teal" variant="light" icon={<CircleCheck size={15} />}>
            <Text size="xs">
              {TEST_STATUS_LABELS.success}
              {connection.last_test_details?.serverVersion
                ? ` · MySQL ${String(connection.last_test_details.serverVersion)}`
                : ''}
              {connection.last_test_details?.latencyMs
                ? ` · ${String(connection.last_test_details.latencyMs)}ms`
                : ''}
            </Text>
          </Alert>
        ) : connection.last_test_status === 'failed' ? (
          <Alert color="red" variant="light" icon={<CircleAlert size={15} />}>
            <Text size="xs">{connection.last_test_error ?? TEST_STATUS_LABELS.failed}</Text>
          </Alert>
        ) : (
          <Alert color="gray" variant="light" icon={<PlugZap size={15} />}>
            <Text size="xs">{TEST_STATUS_LABELS.untested}</Text>
          </Alert>
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
        <Paper component="section" className="next-step-card" withBorder radius="md" p="sm">
          <Group justify="space-between" gap="sm">
            <Group gap={6}>
              <Text className="eyebrow" size="xs" c="dimmed">下一步</Text>
              <Text component="strong" size="sm">{MISSING_STEP_LABELS[step]}</Text>
            </Group>
          </Group>
          <Button
            type="button"
            size="xs"
            rightSection={<ArrowRight size={15} />}
            disabled={isBusy}
            onClick={runNextStep}
          >
            {MISSING_STEP_LABELS[step]}
          </Button>
        </Paper>
      ) : null}
    </div>
  )
}
