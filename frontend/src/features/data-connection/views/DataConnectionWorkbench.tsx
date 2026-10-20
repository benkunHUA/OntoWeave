import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  saveSyncTasks,
  stopSyncRun,
  testDataConnection,
  triggerSync,
  updateDataConnection,
} from '../../../shared/api'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import type { SyncTaskSelection } from '../../../shared/types'
import { ConnectionListPane } from '../components/ConnectionListPane'
import { ConversationPane } from '../components/ConversationPane'
import { PaneResizer } from '../components/PaneResizer'
import { ContextPanel } from '../components/context/ContextPanel'
import { useDataConnectionConversation } from '../hooks/useDataConnectionConversation'
import { useDataConnectionList } from '../hooks/useDataConnectionList'
import { useDataConnectionState } from '../hooks/useDataConnectionState'
import { useContextPaneWidth } from '../hooks/useContextPaneWidth'
import { DATA_CONNECTION_PATH } from '../lib/constants'
import type { ConnectionFormValues } from '../lib/validation'

export function DataConnectionWorkbench() {
  const { connectionId } = useParams()
  const navigate = useNavigate()
  const toast = useErrorToast()
  const list = useDataConnectionList(toast.report)
  const stateView = useDataConnectionState(connectionId)
  const conversationId = stateView.state?.connection.conversation_id
  const conversation = useDataConnectionConversation({
    conversationId,
    refreshState: stateView.refreshState,
    report: toast.report,
    setMessage: toast.setMessage,
    clearMessage: toast.clear,
  })
  const panes = useContextPaneWidth()
  const [isCreating, setIsCreating] = useState(false)
  const [isBusy, setIsBusy] = useState(false)
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(null)

  useEffect(() => {
    if (!connectionId && !list.isLoading && list.items.length) {
      navigate(`${DATA_CONNECTION_PATH}/${list.items[0].id}`, { replace: true })
    }
  }, [connectionId, list.isLoading, list.items, navigate])

  const openConnection = useCallback(
    (targetId: string) => {
      setSelectedDatasetId(null)
      navigate(`${DATA_CONNECTION_PATH}/${targetId}`)
    },
    [navigate],
  )

  async function createConnection() {
    setIsCreating(true)
    try {
      const created = await list.createDraft()
      openConnection(created.id)
    } catch (reason) {
      toast.report(reason, '新建数据连接失败')
    } finally {
      setIsCreating(false)
    }
  }

  async function saveConnection(values: ConnectionFormValues, testAfter: boolean) {
    const connection = stateView.state?.connection
    if (!connection) return
    const operationId = crypto.randomUUID()
    setIsBusy(true)
    try {
      await updateDataConnection(
        connection.id,
        {
          name: values.name.trim(),
          config: {
            host: values.host.trim(),
            port: Number(values.port),
            database: values.database.trim(),
            username: values.username.trim(),
            useSsl: values.useSsl,
          },
          ...(values.password ? { secret: { password: values.password } } : {}),
        },
        operationId,
      )
      if (testAfter) {
        const result = await testDataConnection(connection.id, operationId)
        if (result.status === 'failed') {
          toast.setMessage(result.message ?? '连接测试失败')
        } else {
          toast.clear()
        }
      }
      await Promise.all([stateView.refresh({ withTables: true }), list.refresh()])
    } catch (reason) {
      toast.report(reason, '保存连接失败')
    } finally {
      setIsBusy(false)
    }
  }

  async function testConnection() {
    const connection = stateView.state?.connection
    if (!connection) return
    setIsBusy(true)
    try {
      const result = await testDataConnection(connection.id, crypto.randomUUID())
      if (result.status === 'failed') toast.setMessage(result.message ?? '连接测试失败')
      else toast.clear()
      await Promise.all([stateView.refresh({ withTables: true }), list.refresh()])
    } catch (reason) {
      toast.report(reason, '测试连接失败')
    } finally {
      setIsBusy(false)
    }
  }

  async function persistTasks(selections: SyncTaskSelection[], start: boolean) {
    const connection = stateView.state?.connection
    if (!connection) return
    const operationId = crypto.randomUUID()
    setIsBusy(true)
    try {
      const saved = await saveSyncTasks(connection.id, selections, operationId)
      if (start) {
        await triggerSync(
          connection.id,
          saved.tasks.filter((task) => task.status === 'enabled').map((task) => task.id),
          operationId,
        )
      }
      await stateView.refresh({ withTables: true })
    } catch (reason) {
      toast.report(reason, start ? '开始同步失败' : '保存表选择失败')
    } finally {
      setIsBusy(false)
    }
  }

  async function stopRun(runId: string) {
    setIsBusy(true)
    try {
      await stopSyncRun(runId, crypto.randomUUID())
      await stateView.refreshState()
    } catch (reason) {
      toast.report(reason, '停止同步失败')
    } finally {
      setIsBusy(false)
    }
  }

  const connection = stateView.state?.connection

  return (
    <main className="data-connection-page" style={panes.styleVars}>
      <ConnectionListPane
        connections={list.items}
        activeId={connection?.id ?? null}
        isCreating={isCreating}
        onCreate={() => void createConnection()}
        onSelect={(item) => openConnection(item.id)}
        onHome={() => navigate('/')}
      />
      <div className="data-connection-divider" />
      <ConversationPane
        title={connection?.name ?? ''}
        messages={conversation.messages}
        events={conversation.events}
        isRunning={conversation.agent.isRunning}
        isLoading={conversation.isLoading}
        interrupt={conversation.agent.interrupt}
        error={toast.error}
        onClearError={toast.clear}
        onSend={conversation.send}
        onAnswer={conversation.answer}
        onAbort={conversation.agent.abort}
        onExplain={(summary) => conversation.send(`请解释这次操作失败的原因：${summary}`)}
        onOpenDataset={setSelectedDatasetId}
      />
      <PaneResizer
        dragging={panes.dragging}
        ariaLabel="拖拽调整数据连接详情栏宽度"
        onPointerDown={panes.startResize}
      />
      <ContextPanel
        key={connection?.id ?? 'empty'}
        state={stateView.state}
        tables={stateView.tables}
        warnings={stateView.warnings}
        isLoading={stateView.isLoading}
        isRefreshing={stateView.isRefreshing}
        isBusy={isBusy}
        error={stateView.error}
        selectedDatasetId={selectedDatasetId}
        onSelectDataset={setSelectedDatasetId}
        onRefresh={stateView.refreshAll}
        onReloadTables={stateView.reloadTables}
        onSaveConnection={saveConnection}
        onTestConnection={testConnection}
        onSaveTasks={persistTasks}
        onStopRun={(runId) => void stopRun(runId)}
      />
    </main>
  )
}
