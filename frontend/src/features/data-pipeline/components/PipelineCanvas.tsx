import { CircleAlert, CircleCheck, LayoutGrid, RefreshCw } from 'lucide-react'
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
} from '@xyflow/react'
import { useEffect, useMemo, useRef, useState, type DragEvent } from 'react'
import type {
  NodeCatalog,
  Pipeline,
  PipelineCheckResult,
  PipelineDraftResponse,
  PipelineEdge,
  PipelineNode,
  PipelineNodePositionUpdate,
} from '../../../shared/types'
import {
  CANVAS_COPY,
  CONFLICT_COPY,
  NODE_DRAG_MIME,
  NODE_HEIGHT,
  NODE_WIDTH,
  PIPELINE_STATUS_LABELS,
} from '../lib/constants'
import { validateConnection } from '../lib/connectionValidation'
import {
  changedPositionUpdates,
  draftToFlow,
  layoutPipeline,
  nextNodePosition,
  type PipelineFlowNode,
} from '../lib/flow'
import {
  defaultNodeConfig,
  findNodeDefinition,
  uniqueNodeName,
} from '../lib/nodeCatalog'
import { NodePalette } from './NodePalette'
import { PipelineNode as PipelineNodeView } from './nodes/PipelineNode'

const nodeTypes = { pipeline: PipelineNodeView }

interface PipelineCanvasProps {
  pipeline: Pipeline | null
  draft: PipelineDraftResponse | null
  catalog: NodeCatalog | null
  check: PipelineCheckResult | null
  selectedNodeId: string | null
  isWriting: boolean
  hasConflict: boolean
  error: string | null
  onSelectNode: (nodeId: string | null) => void
  onAddNode: (node: PipelineNode) => Promise<boolean>
  onConnect: (edge: PipelineEdge) => Promise<boolean>
  onDisconnect: (edgeId: string) => Promise<boolean>
  onMove: (positions: PipelineNodePositionUpdate[]) => Promise<unknown> | null
  onClearError: () => void
  onReloadConflict: () => void
}

function CanvasSurface(props: PipelineCanvasProps) {
  const {
    pipeline,
    draft,
    catalog,
    check,
    selectedNodeId,
    isWriting,
    hasConflict,
    error,
    onSelectNode,
    onAddNode,
    onConnect,
    onDisconnect,
    onMove,
    onClearError,
    onReloadConflict,
  } = props
  const flow = useMemo(
    () => (draft ? draftToFlow(draft, catalog, null) : { nodes: [], edges: [] }),
    [catalog, draft],
  )
  const [nodes, setNodes, onNodesChange] = useNodesState(flow.nodes)
  const selectedRef = useRef(selectedNodeId)
  const [connectionError, setConnectionError] = useState<string | null>(null)
  const { screenToFlowPosition, fitView } = useReactFlow<PipelineFlowNode>()

  useEffect(() => {
    selectedRef.current = selectedNodeId
    setNodes((current) => current.map((node) => ({
      ...node,
      selected: node.id === selectedNodeId,
    })))
  }, [selectedNodeId, setNodes])

  useEffect(() => {
    setNodes(flow.nodes.map((node) => ({
      ...node,
      selected: node.id === selectedRef.current,
    })))
  }, [flow.nodes, setNodes])

  async function createNode(type: string, position: { x: number; y: number }) {
    const definition = findNodeDefinition(catalog, type)
    if (!definition || !draft) return
    const node: PipelineNode = {
      id: crypto.randomUUID(),
      type,
      name: uniqueNodeName(definition, draft.definition.nodes),
      config: defaultNodeConfig(definition),
      position: {
        x: Math.round(position.x - NODE_WIDTH / 2),
        y: Math.round(position.y - NODE_HEIGHT / 2),
      },
    }
    if (await onAddNode(node)) onSelectNode(node.id)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    const type = event.dataTransfer.getData(NODE_DRAG_MIME)
    if (!type) return
    void createNode(type, screenToFlowPosition({ x: event.clientX, y: event.clientY }))
  }

  async function handleConnect(connection: Connection) {
    if (!draft || !connection.source || !connection.target) return
    const target = draft.definition.nodes.find((node) => node.id === connection.target)
    const targetDefinition = findNodeDefinition(catalog, target?.type ?? '')
    const targetPort = connection.targetHandle ??
      targetDefinition?.ports.find((port) => port.role === 'input')?.id
    if (!targetPort) return
    const candidate = {
      sourceNodeId: connection.source,
      sourcePort: connection.sourceHandle ?? 'out',
      targetNodeId: connection.target,
      targetPort,
    }
    const validation = validateConnection(draft, candidate, catalog)
    if (!validation.ok) {
      setConnectionError(validation.message)
      return
    }
    setConnectionError(null)
    await onConnect({ id: crypto.randomUUID(), ...candidate })
  }

  function handleEdgesDelete(edges: Edge[]) {
    edges.forEach((edge) => void onDisconnect(edge.id))
  }

  function persistPositions(changed: Node[]) {
    if (!draft) return
    const positions = changedPositionUpdates(draft, changed)
    if (positions.length) void onMove(positions)
  }

  function arrange() {
    if (!draft) return
    const positions = layoutPipeline(draft)
    void onMove(positions)
    window.setTimeout(() => void fitView({ duration: 220, padding: 0.2 }), 80)
  }

  const blocking = check?.blocking_issues.length ?? 0
  const warnings = check?.warnings.length ?? 0

  return (
    <section className="pipeline-canvas-pane">
      <header className="pipeline-canvas-head">
        <div className="pipeline-canvas-title">
          <span className="eyebrow">Data pipeline</span>
          <h1>{pipeline?.name || '数据整理'}</h1>
          <div className="pipeline-canvas-meta">
            {pipeline ? (
              <span className={`status-pill is-${pipeline.status}`}>
                {PIPELINE_STATUS_LABELS[pipeline.status] ?? pipeline.status}
              </span>
            ) : null}
            <span>{CANVAS_COPY.revision} {draft?.revision ?? '—'}</span>
            {check ? (
              <span className={`pipeline-check-chip ${blocking ? 'is-error' : warnings ? 'is-warning' : 'is-ok'}`}>
                {blocking ? <CircleAlert size={12} /> : <CircleCheck size={12} />}
                {blocking ? `${blocking} 个阻塞` : warnings ? `${warnings} 个提醒` : '检查通过'}
              </span>
            ) : null}
          </div>
        </div>
        <div className="pipeline-canvas-actions">
          <button type="button" className="secondary-button" onClick={arrange} disabled={!draft || isWriting}>
            <LayoutGrid size={14} />
            {CANVAS_COPY.fit}
          </button>
          <button
            type="button"
            className="icon-button"
            aria-label="适应画布"
            onClick={() => void fitView({ duration: 180, padding: 0.2 })}
          >
            <RefreshCw size={15} />
          </button>
        </div>
      </header>

      {hasConflict ? (
        <div className="pipeline-canvas-banner is-error">
          <div>
            <strong>{CONFLICT_COPY.title}</strong>
            <span>{CONFLICT_COPY.body}</span>
          </div>
          <button type="button" className="secondary-button" onClick={onReloadConflict}>
            {CONFLICT_COPY.reload}
          </button>
        </div>
      ) : null}
      {error || connectionError ? (
        <div className="pipeline-canvas-banner is-error">
          <span>{connectionError ?? error}</span>
          <button
            type="button"
            className="text-button"
            onClick={() => {
              setConnectionError(null)
              onClearError()
            }}
          >
            关闭
          </button>
        </div>
      ) : null}

      <div className="pipeline-canvas-body">
        <NodePalette catalog={catalog} onAdd={(type) => void createNode(type, nextNodePosition(draft))} />
        <div
          className="pipeline-flow"
          onDrop={handleDrop}
          onDragOver={(event) => {
            event.preventDefault()
            event.dataTransfer.dropEffect = 'move'
          }}
        >
          <ReactFlow<PipelineFlowNode, Edge>
            nodes={nodes}
            edges={flow.edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onNodeClick={(_, node) => onSelectNode(node.id)}
            onNodeDragStop={(_, node, dragged) => persistPositions(dragged.length ? dragged : [node])}
            onSelectionDragStop={(_, dragged) => persistPositions(dragged)}
            onConnect={(connection) => void handleConnect(connection)}
            onEdgesDelete={handleEdgesDelete}
            onEdgeDoubleClick={(_, edge) => void onDisconnect(edge.id)}
            onPaneClick={() => onSelectNode(null)}
            deleteKeyCode={null}
            fitView
            minZoom={0.3}
            maxZoom={1.6}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1} color="#363636" />
            <Controls showInteractive={false} />
          </ReactFlow>
          {!draft?.definition.nodes.length ? (
            <div className="pipeline-canvas-empty">
              <strong>{pipeline ? '画布还是空的' : '选择一条流程'}</strong>
              <p>{pipeline ? '从节点面板拖入或点击节点开始搭建。' : '在左侧新建或选择一条数据整理流程。'}</p>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  )
}

export function PipelineCanvas(props: PipelineCanvasProps) {
  return (
    <ReactFlowProvider>
      <CanvasSurface {...props} />
    </ReactFlowProvider>
  )
}
