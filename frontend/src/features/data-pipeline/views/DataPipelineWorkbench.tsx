import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import type {
  JsonObject,
  Pipeline,
  PipelineEdge,
  PipelineNode,
  PipelineNodePositionUpdate,
  StateUpdateEvent,
} from '../../../shared/types'
import { WorkbenchPaneLayout } from '../../../shared/workbench/WorkbenchPaneLayout'
import { NodeConfigDrawer } from '../components/drawers/NodeConfigDrawer'
import { PipelineCanvas } from '../components/PipelineCanvas'
import { PipelineConversationPane } from '../components/PipelineConversationPane'
import { PipelineListPane } from '../components/PipelineListPane'
import { usePipelineCommandQueue } from '../hooks/usePipelineCommandQueue'
import { usePipelineConversation } from '../hooks/usePipelineConversation'
import { usePipelineDraft } from '../hooks/usePipelineDraft'
import { usePipelineLayout } from '../hooks/usePipelineLayout'
import { usePipelineList } from '../hooks/usePipelineList'
import { DATA_PIPELINE_PATH, PIPELINE_LIST_WIDTH } from '../lib/constants'
import { pipelineStateFromEvent } from '../lib/draft'
import { findNodeDefinition } from '../lib/nodeCatalog'

export function DataPipelineWorkbench() {
  const { pipelineId } = useParams()
  const navigate = useNavigate()
  const toast = useErrorToast()
  const list = usePipelineList()
  const draftView = usePipelineDraft(pipelineId)
  const queue = usePipelineCommandQueue({
    pipelineId,
    draft: draftView.draft,
    setDraft: draftView.setDraft,
    refreshCheck: draftView.refreshCheck,
    reload: draftView.reload,
  })
  const {
    addNode: queueAddNode,
    connectNodes: queueConnectNodes,
    disconnectNodes: queueDisconnectNodes,
    moveNodes: queueMoveNodes,
    updateNode: queueUpdateNode,
    deleteNode: queueDeleteNode,
    setOutput: queueSetOutput,
  } = queue
  const { reload: reloadDraft, setCheck: setDraftCheck } = draftView
  const { create: createPipelineRecord } = list
  const layout = usePipelineLayout()
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)

  const pipeline = useMemo<Pipeline | null>(
    () => list.items.find((item) => item.id === pipelineId) ?? null,
    [list.items, pipelineId],
  )

  const handleStateUpdate = useCallback(
    (event: StateUpdateEvent) => {
      const state = pipelineStateFromEvent(event)
      if (!state || state.pipelineId !== pipelineId) return
      if (state.check) setDraftCheck(state.check)
      if (state.draftRevision !== null) void reloadDraft()
    },
    [pipelineId, reloadDraft, setDraftCheck],
  )

  const conversation = usePipelineConversation({
    conversationId: pipeline?.conversation_id,
    report: toast.report,
    setMessage: toast.setMessage,
    clearMessage: toast.clear,
    onChanged: () => {
      void list.refresh()
      void draftView.reload()
    },
    onStateUpdate: handleStateUpdate,
  })

  useEffect(() => {
    if (pipelineId || list.isLoading || !list.items.length) return
    navigate(`${DATA_PIPELINE_PATH}/${list.items[0].id}`, { replace: true })
  }, [list.isLoading, list.items, navigate, pipelineId])

  useEffect(() => {
    setSelectedNodeId(null)
  }, [pipelineId])

  const selectedNode = useMemo(
    () => draftView.draft?.definition.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [draftView.draft, selectedNodeId],
  )

  useEffect(() => {
    if (selectedNodeId && !selectedNode) setSelectedNodeId(null)
  }, [selectedNode, selectedNodeId])

  const selectedIssues = useMemo(() => {
    if (!draftView.check || !selectedNode) return []
    return [...draftView.check.blocking_issues, ...draftView.check.warnings]
      .filter((issue) => issue.node_id === selectedNode.id)
  }, [draftView.check, selectedNode])

  const addNode = useCallback(
    async (node: PipelineNode) => Boolean(await queueAddNode(node)),
    [queueAddNode],
  )
  const connectNodes = useCallback(
    async (edge: PipelineEdge) => Boolean(await queueConnectNodes(edge)),
    [queueConnectNodes],
  )
  const disconnectNodes = useCallback(
    async (edgeId: string) => Boolean(await queueDisconnectNodes(edgeId)),
    [queueDisconnectNodes],
  )
  const moveNodes = useCallback(
    (positions: PipelineNodePositionUpdate[]) => queueMoveNodes(positions),
    [queueMoveNodes],
  )
  const saveNode = useCallback(
    (nodeId: string, name: string, config: JsonObject) =>
      queueUpdateNode(nodeId, name, config),
    [queueUpdateNode],
  )
  const deleteNode = useCallback(
    async (nodeId: string) => {
      const response = await queueDeleteNode(nodeId)
      if (response) setSelectedNodeId(null)
      return response
    },
    [queueDeleteNode],
  )

  const createPipeline = useCallback(
    async (name: string) => {
      const created = await createPipelineRecord(name)
      if (created) navigate(`${DATA_PIPELINE_PATH}/${created.id}`)
      return created
    },
    [createPipelineRecord, navigate],
  )

  return (
    <WorkbenchPaneLayout
      pageClassName="data-pipeline-page"
      dividerClassName="data-pipeline-divider"
      leftWidth={PIPELINE_LIST_WIDTH}
      rightWidth={layout.width}
      dragging={layout.dragging}
      resizerLabel="拖拽调整流程画布宽度"
      onStartResize={layout.startResize}
      styleVars={layout.styleVars}
      left={<PipelineListPane
        pipelines={list.items}
        activeId={pipelineId ?? null}
        isLoading={list.isLoading}
        error={list.error}
        onCreate={createPipeline}
        onSelect={(item) => navigate(`${DATA_PIPELINE_PATH}/${item.id}`)}
        onHome={() => navigate('/')}
      />}
      center={<PipelineConversationPane
        title={pipeline?.name ?? ''}
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
      />}
      right={<div className="pipeline-workspace">
        <PipelineCanvas
          pipeline={pipeline}
          draft={draftView.draft}
          catalog={draftView.catalog}
          check={draftView.check}
          selectedNodeId={selectedNodeId}
          isWriting={queue.isWriting}
          hasConflict={queue.hasConflict}
          error={queue.error ?? draftView.error}
          onSelectNode={setSelectedNodeId}
          onAddNode={addNode}
          onConnect={connectNodes}
          onDisconnect={disconnectNodes}
          onMove={moveNodes}
          onClearError={queue.clearError}
          onReloadConflict={queue.resumeAfterConflict}
        />
        {selectedNode ? (
          <NodeConfigDrawer
            key={selectedNode.id}
            node={selectedNode}
            definition={findNodeDefinition(draftView.catalog, selectedNode.type)}
            issues={selectedIssues}
            isSaving={queue.isWriting}
            isReadOnly={queue.hasConflict}
            onClose={() => setSelectedNodeId(null)}
            onSave={saveNode}
            onDelete={deleteNode}
            onSetOutput={queueSetOutput}
          />
        ) : null}
      </div>}
    />
  )
}
