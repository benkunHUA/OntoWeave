import {
  Background,
  BackgroundVariant,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Edge,
  type NodeMouseHandler,
} from '@xyflow/react'
import {
  Bookmark,
  Compass,
  Focus,
  Maximize2,
  Minus,
  Plus,
} from 'lucide-react'
import { useEffect } from 'react'
import { buildFlowElements, type OntologyFlowNode } from '../lib/flow'
import { OntologyNode } from './OntologyNode'
import type { Changeset, GraphSnapshot, ViewSpec } from '../../../shared/types'

const nodeTypes = { ontology: OntologyNode }

interface OntologyCanvasProps {
  snapshot: GraphSnapshot | null
  viewSpec: ViewSpec
  changeset: Changeset | null
  selectedNodeId: string | null
  onSelectNode: (nodeId: string | null) => void
  onLayoutChange: (kind: NonNullable<ViewSpec['layout']>['kind']) => void
  onMoveNode: (nodeId: string, x: number, y: number) => void
  onSaveView: () => void
}

function CanvasInner({
  snapshot,
  viewSpec,
  changeset,
  selectedNodeId,
  onSelectNode,
  onLayoutChange,
  onMoveNode,
  onSaveView,
}: OntologyCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<OntologyFlowNode>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const { fitView, zoomIn, zoomOut } = useReactFlow()

  useEffect(() => {
    const elements = buildFlowElements(snapshot, viewSpec, changeset)
    setNodes(
      elements.nodes.map((node) => ({
        ...node,
        selected: node.id === selectedNodeId,
      })),
    )
    setEdges(elements.edges)
    window.setTimeout(() => fitView({ padding: 0.2, duration: 320 }), 0)
  }, [snapshot, viewSpec, changeset, selectedNodeId, setNodes, setEdges, fitView])

  const handleNodeClick: NodeMouseHandler<OntologyFlowNode> = (_, node) => {
    onSelectNode(node.id)
  }

  const draftCount =
    snapshot?.nodes.filter((node) => node.status === 'draft').length ?? 0
  const confirmedCount = (snapshot?.nodes.length ?? 0) - draftCount

  return (
    <section className="canvas-panel" aria-label="图">
      <header className="canvas-toolbar">
        <div className="canvas-toolbar__title">
          <Compass size={18} />
          <div>
            <strong>{snapshot?.graph.name || '本体图'}</strong>
            <span>
              {snapshot?.nodes.length ?? 0} 个对象 · {snapshot?.relations.length ?? 0} 条关系
            </span>
          </div>
        </div>
        <div className="canvas-toolbar__actions">
          <label className="toolbar-select">
            <span>布局</span>
            <select
              value={viewSpec.layout?.kind ?? 'hierarchical'}
              onChange={(event) =>
                onLayoutChange(
                  event.target.value as NonNullable<ViewSpec['layout']>['kind'],
                )
              }
            >
              <option value="hierarchical">层级</option>
              <option value="force">力导向</option>
              <option value="domain-grouped">按域分组</option>
              <option value="grid">网格</option>
            </select>
          </label>
          <button type="button" className="icon-button" aria-label="缩小" onClick={() => zoomOut()}>
            <Minus size={17} />
          </button>
          <button type="button" className="icon-button" aria-label="放大" onClick={() => zoomIn()}>
            <Plus size={17} />
          </button>
          <button
            type="button"
            className="icon-button"
            aria-label="适应画面"
            onClick={() => fitView({ padding: 0.2, duration: 260 })}
          >
            <Maximize2 size={17} />
          </button>
          <button
            type="button"
            className="icon-button"
            aria-label="保存视图"
            onClick={onSaveView}
          >
            <Bookmark size={17} />
          </button>
        </div>
      </header>

      <div className="canvas-stage">
        {snapshot?.nodes.length ? (
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onNodeClick={handleNodeClick}
            onPaneClick={() => onSelectNode(null)}
            onNodeDragStop={(_, node) => onMoveNode(node.id, node.position.x, node.position.y)}
            fitView
            minZoom={0.25}
            maxZoom={1.8}
            proOptions={{ hideAttribution: true }}
          >
            <Background
              variant={BackgroundVariant.Dots}
              gap={22}
              size={1}
              color="#d7dee8"
            />
          </ReactFlow>
        ) : (
          <div className="canvas-empty">
            <span className="canvas-empty__icon">
              <Focus size={26} />
            </span>
            <strong>还没有可显示的对象</strong>
            <p>在对话里描述业务，草稿会出现在这里。</p>
          </div>
        )}
      </div>

      <footer className="canvas-footer">
        <span className="legend-item">
          <i className="legend-line legend-line--confirmed" />
          已确认 {confirmedCount}
        </span>
        <span className="legend-item">
          <i className="legend-line legend-line--draft" />
          草稿 {draftCount}
        </span>
        {changeset?.status === 'open' ? (
          <span className="canvas-footer__draft-state">改动待确认</span>
        ) : null}
      </footer>
    </section>
  )
}

export function OntologyCanvas(props: OntologyCanvasProps) {
  return (
    <ReactFlowProvider>
      <CanvasInner {...props} />
    </ReactFlowProvider>
  )
}
