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
        <div className="context-empty">
          <Database size={24} />
          <strong>新建连接后在这里配置</strong>
          <p>{error ?? (isLoading ? '正在读取连接状态…' : '左侧选择或新建一个数据连接。')}</p>
        </div>
      </aside>
    )
  }

  const { connection } = state
  const showForm = connection.status === 'draft' || editing

  return (
    <aside className="context-panel">
      <header className="context-panel__header">
        <div>
          <span className="eyebrow">{connection.kind.toUpperCase()}</span>
          <h1>{connection.name}</h1>
          <div className="context-panel__badges">
            <span className={`status-pill is-${connection.status}`}>
              {CONNECTION_STATUS_LABELS[connection.status]}
            </span>
            <span className={`status-pill is-test-${connection.last_test_status}`}>
              {TEST_STATUS_LABELS[connection.last_test_status]}
            </span>
            {state.missing_steps.slice(0, 2).map((step) => (
              <span key={step} className="missing-step-pill">
                {MISSING_STEP_LABELS[step as keyof typeof MISSING_STEP_LABELS] ?? step}
              </span>
            ))}
          </div>
        </div>
        <div className="context-panel__tools">
          <button type="button" className="icon-button" aria-label="刷新状态" onClick={() => void onRefresh()}>
            <RefreshCw size={15} className={isRefreshing ? 'is-spinning' : ''} />
          </button>
          {connection.status === 'active' && !showForm ? (
            <button type="button" className="icon-button" aria-label="编辑连接" onClick={() => setEditing(true)}>
              <Pencil size={15} />
            </button>
          ) : null}
        </div>
      </header>

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
          <DatasetDetail datasetId={selectedDatasetId} onBack={() => onSelectDataset(null)} />
        </div>
      ) : (
        <>
          <nav className="context-tabs" aria-label="连接上下文">
            {CONTEXT_TABS.map((item) => (
              <button
                type="button"
                key={item.id}
                className={tab === item.id ? 'is-active' : ''}
                onClick={() => {
                  setTab(item.id)
                  setSchemaTarget(null)
                }}
              >
                {item.label}
              </button>
            ))}
          </nav>
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
                    warnings={warnings}
                    isBusy={isBusy}
                    onRefresh={onRefresh}
                    onOpenSchema={(schema, table) => setSchemaTarget({ schema, table })}
                    onSave={onSaveTasks}
                  />
                  <SyncTaskList tasks={state.tasks} runs={state.recent_runs} isBusy={isBusy} onStop={onStopRun} />
                </>
              )
            ) : null}
            {tab === 'datasets' ? (
              <DatasetsTab datasets={state.datasets} onSelect={onSelectDataset} />
            ) : null}
          </div>
        </>
      )}
    </aside>
  )
}

function DatasetsTab({ datasets, onSelect }: { datasets: Dataset[]; onSelect: (id: string) => void }) {
  return (
    <div className="datasets-tab">
      {datasets.length ? (
        <DatasetList datasets={datasets} onSelect={onSelect} />
      ) : (
        <div className="empty-panel">同步成功后，数据集会显示在这里</div>
      )}
    </div>
  )
}
