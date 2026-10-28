import {
  ActionIcon,
  Badge,
  Group,
  Stack,
  Tabs,
  Text,
  Title,
} from '@mantine/core'
import { Database, Pencil, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import type {
  DataConnectionState,
  Dataset,
  SyncTaskSelection,
  TableSummary,
} from '../../../../shared/types'
import { CONTEXT_TABS, type ContextTab } from '../../lib/constants'
import { CONNECTION_STATUS_LABELS, MISSING_STEP_LABELS, TEST_STATUS_LABELS } from '../../lib/labels'
import { EmptyState } from '../../../../shared/components/EmptyState'
import type { ConnectionFormValues } from '../../lib/validation'
import { ConnectionForm } from './ConnectionForm'
import { ConnectionOverview } from './ConnectionOverview'
import { DatasetDetail } from './DatasetDetail'
import { DatasetList } from './DatasetList'
import { SyncTaskList } from './SyncTaskList'
import { TablePicker } from './TablePicker'
import { TableSchemaPanel } from './TableSchemaPanel'

interface ContextPanelProps {
  state: DataConnectionState | null
  tables: TableSummary[]
  warnings: string[]
  isLoading: boolean
  isRefreshing: boolean
  isBusy: boolean
  error: string | null
  selectedDatasetId: string | null
  onSelectDataset: (datasetId: string | null) => void
  onRefresh: () => Promise<void>
  onReloadTables: () => Promise<void>
  onSaveConnection: (values: ConnectionFormValues, testAfter: boolean) => Promise<void>
  onTestConnection: () => Promise<void>
  onSaveTasks: (selections: SyncTaskSelection[], start: boolean) => Promise<void>
  onStopRun: (runId: string) => void
}

export function ContextPanel({
  state,
  tables,
  warnings,
  isLoading,
  isRefreshing,
  isBusy,
  error,
  selectedDatasetId,
  onSelectDataset,
  onRefresh,
  onReloadTables,
  onSaveConnection,
  onTestConnection,
  onSaveTasks,
  onStopRun,
}: ContextPanelProps) {
  const [tab, setTab] = useState<ContextTab>('overview')
  const [editing, setEditing] = useState(false)
  const [schemaTarget, setSchemaTarget] = useState<{ schema: string; table: string } | null>(null)

  if (!state) {
    return (
      <aside className="context-panel">
        <EmptyState
          className="context-empty"
          icon={<Database size={22} />}
          title="新建连接后在这里配置"
          body={error ?? (isLoading ? '正在读取连接状态…' : '左侧选择或新建一个数据连接。')}
        />
      </aside>
    )
  }

  const { connection } = state
  const showForm = connection.status === 'draft' || editing

  return (
    <aside className="context-panel">
      <Group className="context-panel__header" justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap="xs">
          <Text className="eyebrow" size="xs" c="dimmed">{connection.kind.toUpperCase()}</Text>
          <Title order={1}>{connection.name}</Title>
          <Group className="context-panel__badges" gap={6}>
            <Badge color={connection.status === 'active' ? 'teal' : 'gray'} variant="light">
              {CONNECTION_STATUS_LABELS[connection.status]}
            </Badge>
            <Badge color={connection.last_test_status === 'success' ? 'teal' : connection.last_test_status === 'failed' ? 'red' : 'gray'} variant="light">
              {TEST_STATUS_LABELS[connection.last_test_status]}
            </Badge>
            {state.missing_steps.slice(0, 2).map((step) => (
              <Badge key={step} color="yellow" variant="light">
                {MISSING_STEP_LABELS[step as keyof typeof MISSING_STEP_LABELS] ?? step}
              </Badge>
            ))}
          </Group>
        </Stack>
        <Group className="context-panel__tools" gap="xs">
          <ActionIcon type="button" variant="subtle" color="gray" aria-label="刷新状态" onClick={() => void onRefresh()}>
            <RefreshCw size={15} className={isRefreshing ? 'is-spinning' : ''} />
          </ActionIcon>
          {connection.status === 'active' && !showForm ? (
            <ActionIcon type="button" variant="subtle" color="gray" aria-label="编辑连接" onClick={() => setEditing(true)}>
              <Pencil size={15} />
            </ActionIcon>
          ) : null}
        </Group>
      </Group>

      {showForm ? (
        <div className="context-panel__body">
          <ConnectionForm
            key={`${connection.id}:${connection.config_version}`}
            connection={connection}
            isBusy={isBusy}
            onSave={async (values, testAfter) => {
              await onSaveConnection(values, testAfter)
              if (connection.status === 'active') setEditing(false)
            }}
          />
        </div>
      ) : selectedDatasetId ? (
        <div className="context-panel__body">
          <DatasetDetail
            datasetId={selectedDatasetId}
            connectionName={connection.name}
            onBack={() => onSelectDataset(null)}
          />
        </div>
      ) : (
        <>
          <Tabs
            className="context-tabs"
            value={tab}
            onChange={(value) => {
              if (!value) return
              const next = value as ContextTab
              setTab(next)
              setSchemaTarget(null)
              if (next === 'tables') void onReloadTables()
            }}
            aria-label="连接上下文"
          >
            <Tabs.List>
              {CONTEXT_TABS.map((item) => (
                <Tabs.Tab key={item.id} value={item.id}>{item.label}</Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs>
          <div className="context-panel__body">
            {tab === 'overview' ? (
              <ConnectionOverview
                state={state}
                isBusy={isBusy}
                onEdit={() => setEditing(true)}
                onTest={() => void onTestConnection()}
                onOpenTab={setTab}
              />
            ) : null}
            {tab === 'tables' ? (
              schemaTarget ? (
                <TableSchemaPanel
                  connectionId={connection.id}
                  schema={schemaTarget.schema}
                  table={schemaTarget.table}
                  onBack={() => setSchemaTarget(null)}
                />
              ) : (
                <>
                  <TablePicker
                    tasks={state.tasks}
                    tables={tables}
                    runs={state.recent_runs}
                    warnings={warnings}
                    isBusy={isBusy}
                    onRefresh={onReloadTables}
                    onOpenSchema={(schema, table) => setSchemaTarget({ schema, table })}
                    onSave={onSaveTasks}
                  />
                  <SyncTaskList tasks={state.tasks} runs={state.recent_runs} isBusy={isBusy} onStop={onStopRun} />
                </>
              )
            ) : null}
            {tab === 'datasets' ? (
              <DatasetsTab
                datasets={state.datasets}
                runs={state.recent_runs}
                onSelect={onSelectDataset}
              />
            ) : null}
          </div>
        </>
      )}
    </aside>
  )
}

function DatasetsTab({
  datasets,
  runs,
  onSelect,
}: {
  datasets: Dataset[]
  runs: DataConnectionState['recent_runs']
  onSelect: (id: string) => void
}) {
  return (
    <div className="datasets-tab">
      {datasets.length ? (
        <DatasetList datasets={datasets} runs={runs} onSelect={onSelect} />
      ) : (
        <EmptyState title="同步成功后，数据集会显示在这里" />
      )}
    </div>
  )
}
