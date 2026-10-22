import { useCallback, useEffect, useMemo, useRef } from 'react'
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
import {
  NodeConfigDrawer,
  type NodeConfigDrawerHandle,
} from '../components/drawers/NodeConfigDrawer'
import { BuildBar } from '../components/build/BuildBar'
import { BuildDetailsDrawer } from '../components/build/BuildDetailsDrawer'
import { PipelineCanvas } from '../components/PipelineCanvas'
import { PipelineConversationPane } from '../components/PipelineConversationPane'
import { PipelineListPane } from '../components/PipelineListPane'
import { PreviewPanel } from '../components/preview/PreviewPanel'
import { ScheduleDrawer } from '../components/schedule/ScheduleDrawer'
import { usePipelineCommandQueue } from '../hooks/usePipelineCommandQueue'
import { usePipelineConversation } from '../hooks/usePipelineConversation'
import { usePipelineDraft } from '../hooks/usePipelineDraft'
import { usePipelineExecutionUi } from '../hooks/usePipelineExecutionUi'
import { usePipelineLayout } from '../hooks/usePipelineLayout'
import { usePipelineList } from '../hooks/usePipelineList'
import {
  DATA_PIPELINE_PATH,
  PIPELINE_DEFAULT_NAME,
  PIPELINE_LIST_WIDTH,
} from '../lib/constants'
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
  const {
    archive: archivePipelineRecord,
    create: createPipelineRecord,
    rename: renamePipelineRecord,
  } = list
  const refreshList = list.refresh
  const reloadDraftView = draftView.reload
  const layout = usePipelineLayout()
  const nodeDrawerRef = useRef<NodeConfigDrawerHandle | null>(null)

  const pipeline = useMemo<Pipeline | null>(
    () => list.items.find((item) => item.id === pipelineId) ?? null,
    [list.items, pipelineId],
  )
  const handleBuildSucceeded = useCallback(() => {
    void refreshList()
    void reloadDraftView()
  }, [refreshList, reloadDraftView])
  const execution = usePipelineExecutionUi({
    pipelineId,
    draft: draftView.draft,
    check: draftView.check,
    onBuildSucceeded: handleBuildSucceeded,
  })
  const {
    preview,
    build,
    outputs,
    outputNames,
    selectedNode,
    selectedIssues,
    previewNode,
    focusField,
    scheduleOpen,
    setScheduleOpen,
    selectNode,
    openPreview,
    openIssue,
    openBuildDetails,
    openSchedule,
    createBuild,
    closePanel,
    closePreview,
  } = execution

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
    (targetPipelineId: string, nodeId: string, name: string, config: JsonObject) => {
      if (targetPipelineId !== pipelineId) return null
      return queueUpdateNode(nodeId, name, config)
    },
    [pipelineId, queueUpdateNode],
  )
  const deleteNode = useCallback(
    async (nodeId: string) => {
      const response = await queueDeleteNode(nodeId)
      if (response) closePanel()
      return response
    },
    [closePanel, queueDeleteNode],
  )

  const createPipeline = useCallback(
    async () => {
      const created = await createPipelineRecord(PIPELINE_DEFAULT_NAME)
      if (created) navigate(`${DATA_PIPELINE_PATH}/${created.id}`)
      return created
    },
    [createPipelineRecord, navigate],
  )

  const renamePipeline = useCallback(
    async (name: string) => {
      if (!pipelineId) return false
      return Boolean(await renamePipelineRecord(pipelineId, name))
    },
    [pipelineId, renamePipelineRecord],
  )

  const deletePipeline = useCallback(
    async (target: Pipeline) => {
      const deleted = await archivePipelineRecord(target.id)
      if (!deleted || target.id !== pipelineId) return deleted
      const next = list.items.find((item) => item.id !== target.id)
      navigate(next ? `${DATA_PIPELINE_PATH}/${next.id}` : DATA_PIPELINE_PATH, { replace: true })
      return true
    },
    [archivePipelineRecord, list.items, navigate, pipelineId],
  )

  const prepareBuild = useCallback(async () => {
    if (!nodeDrawerRef.current) return draftView.draft?.revision ?? null
    return nodeDrawerRef.current.flush()
  }, [draftView.draft?.revision])

  return (
    <WorkbenchPaneLayout
      pageClassName="data-pipeline-page"
      dividerClassName="data-pipeline-divider"
      leftWidth={PIPELINE_LIST_WIDTH}
      centerWidth={layout.width}
      dragging={layout.dragging}
      resizerLabel="拖拽调整对话栏宽度"
      onStartResize={layout.startResize}
      left={<PipelineListPane
        pipelines={list.items}
        activeId={pipelineId ?? null}
        isLoading={list.isLoading}
        error={list.error}
        onCreate={createPipeline}
        onDelete={deletePipeline}
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
          selectedNodeId={selectedNode?.id ?? null}
          isWriting={queue.isWriting}
          hasConflict={queue.hasConflict}
          error={queue.error ?? draftView.error}
          actions={<BuildBar
            revision={draftView.draft?.revision ?? null}
            check={draftView.check}
            outputs={outputs}
            isWriting={queue.isWriting}
            build={build.build}
            isCreating={build.isLoading}
            error={build.error}
            onPrepareBuild={prepareBuild}
            onBuild={(revision) => void createBuild(revision)}
            onOpenDetails={openBuildDetails}
            onOpenSchedule={openSchedule}
          />}
          onSelectNode={selectNode}
          onAddNode={addNode}
          onConnect={connectNodes}
          onDisconnect={disconnectNodes}
          onMove={moveNodes}
          onRename={renamePipeline}
          onClearError={queue.clearError}
          onReloadConflict={queue.resumeAfterConflict}
        />
        {selectedNode && pipelineId ? (
          <NodeConfigDrawer
            ref={nodeDrawerRef}
            key={`${pipelineId}:${selectedNode.id}`}
            pipelineId={pipelineId}
            revision={draftView.draft?.revision ?? null}
            node={selectedNode}
            definition={findNodeDefinition(draftView.catalog, selectedNode.type)}
            issues={selectedIssues}
            isSaving={queue.isWriting}
            isReadOnly={queue.hasConflict}
            focusField={focusField}
            onClose={closePanel}
            onPreview={(nodeId, revision) => openPreview(nodeId, revision)}
            onSave={saveNode}
            onDelete={deleteNode}
            onSetOutput={queueSetOutput}
            latestBuild={build.build}
            onOpenBuildDetails={openBuildDetails}
          />
        ) : null}
        {previewNode ? (
          <PreviewPanel
            nodeName={previewNode.name}
            draftRevision={draftView.draft?.revision ?? null}
            preview={preview.preview}
            state={preview.state}
            error={preview.error}
            isLoading={preview.isLoading}
            onRefresh={() => void preview.run(previewNode.id)}
            onClose={closePreview}
            onSelectIssue={openIssue}
          />
        ) : null}
        {build.detailsOpen ? (
          <BuildDetailsDrawer
            pipelineId={pipelineId ?? ''}
            build={build.build}
            outputNames={outputNames}
            isLoading={build.isLoading}
            error={build.error}
            onClose={() => build.setDetailsOpen(false)}
            onCancel={build.cancel}
            onOpenBuild={openBuildDetails}
          />
        ) : null}
        {scheduleOpen ? (
          <ScheduleDrawer
            open
            pipeline={pipeline}
            draftRevision={draftView.draft?.revision ?? null}
            onClose={() => setScheduleOpen(false)}
            onOpenBuild={openBuildDetails}
          />
        ) : null}
      </div>}
    />
  )
}
