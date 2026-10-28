import {
  Background,
  BackgroundVariant,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Edge,
  type EdgeMouseHandler,
  type NodeMouseHandler,
} from '@xyflow/react'
import { ActionIcon, Group, Text } from '@mantine/core'
import {
  Bookmark,
  Compass,
  Focus,
  Maximize2,
  Minus,
  Plus,
} from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { buildFlowElements, type FlowNode } from '../lib/flow'
import { hasPendingOntologyChanges } from '../lib/changesetSummary'
import { ActionNode } from './action/ActionNode'
import { OntologyNode } from './OntologyNode'
import { NativeSelectField } from '../../../shared/components/form/controls'
import type { Changeset, GraphSnapshot, ViewSpec } from '../../../shared/types'

const nodeTypes = { ontology: OntologyNode, action: ActionNode }

interface OntologyCanvasProps {
  snapshot: GraphSnapshot | null
  viewSpec: ViewSpec
  changeset: Changeset | null
  selectedNodeId: string | null
  selectedRelationId: string | null
  selectedActionId: string | null
  onSelectNode: (nodeId: string | null) => void
  onSelectRelation: (relationId: string | null) => void
  onSelectAction: (actionId: string | null) => void
  onLayoutChange: (kind: NonNullable<ViewSpec['layout']>['kind']) => void
  onMoveNode: (nodeId: string, x: number, y: number) => void
  onSaveView: () => void
  toolbarExtra?: ReactNode
}

function CanvasInner({
  snapshot,
  viewSpec,
  changeset,
  selectedNodeId,
  selectedRelationId,
  selectedActionId,
  onSelectNode,
  onSelectRelation,
  onSelectAction,
  onLayoutChange,
  onMoveNode,
  onSaveView,
  toolbarExtra,
}: OntologyCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [zoom, setZoom] = useState(1)
  const { fitView, zoomIn, zoomOut } = useReactFlow()

  useEffect(() => {
    const elements = buildFlowElements(snapshot, viewSpec, changeset)
    setNodes(
      elements.nodes.map((node) => ({
        ...node,
        selected: node.id === selectedNodeId || node.id === selectedActionId,
      })),
    )
    setEdges(
      elements.edges.map((edge) => {
        if (edge.id !== selectedRelationId) return { ...edge, selected: false }
        // 选中态加粗提亮，盖过 flow.ts 里的常规描边。
        return {
          ...edge,
          selected: true,
          style: { ...edge.style, stroke: '#9dc0ff', strokeWidth: 3 },
        }
      }),
    )
    window.setTimeout(() => fitView({ padding: 0.2, duration: 320 }), 0)
  }, [
    snapshot,
    viewSpec,
    changeset,
    selectedNodeId,
    selectedRelationId,
    selectedActionId,
    setNodes,
    setEdges,
    fitView,
  ])

  const handleNodeClick: NodeMouseHandler<FlowNode> = (_, node) => {
    if (node.type === 'action') onSelectAction(node.id)
    else onSelectNode(node.id)
  }

  const handleEdgeClick: EdgeMouseHandler = (_, edge) => {
    // 动作与本体的连线是跟着定义自动生成的，点它不该开关系面板。
    if (edge.data?.involves) return
    onSelectRelation(edge.id)
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
              {snapshot?.nodes.length ?? 0} 个对象 · {snapshot?.relations.length ?? 0} 条关系 ·{' '}
              {snapshot?.actions.length ?? 0} 个动作
            </span>
          </div>
        </div>
        <div className="canvas-toolbar__actions">
          <label className="toolbar-select">
            <span>布局</span>
            <NativeSelectField
              className="toolbar-select__control"
              size="xs"
              value={viewSpec.layout?.kind ?? 'hierarchical'}
              data={[
                { value: 'hierarchical', label: '层级' },
                { value: 'force', label: '力导向' },
                { value: 'domain-grouped', label: '按域分组' },
                { value: 'grid', label: '网格' },
              ]}
              onChange={(event) =>
                onLayoutChange(
                  event.target.value as NonNullable<ViewSpec['layout']>['kind'],
                )
              }
            />
          </label>
          {toolbarExtra}
        </div>
      </header>

      <div className="canvas-stage">
        {snapshot?.nodes.length || snapshot?.actions.length ? (
          <>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={nodeTypes}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onNodeClick={handleNodeClick}
              onEdgeClick={handleEdgeClick}
              onPaneClick={() => {
                onSelectNode(null)
                onSelectAction(null)
              }}
              onNodeDragStop={(_, node) =>
                onMoveNode(node.id, node.position.x, node.position.y)
              }
              onMove={(_, viewport) => setZoom(viewport.zoom)}
              fitView
              minZoom={0.25}
              maxZoom={1.8}
              proOptions={{ hideAttribution: true }}
            >
              <Background
                variant={BackgroundVariant.Dots}
                gap={22}
                size={1}
                color="#2c2c2c"
              />
            </ReactFlow>
            <Group className="canvas-controls" gap={4} wrap="nowrap">
              <ActionIcon
                type="button"
                variant="subtle"
                color="gray"
                size="sm"
                aria-label="缩小"
                onClick={() => zoomOut()}
              >
                <Minus size={16} />
              </ActionIcon>
              <Text className="canvas-controls__zoom" component="span" size="xs" fw={700}>{Math.round(zoom * 100)}%</Text>
              <ActionIcon
                type="button"
                variant="subtle"
                color="gray"
                size="sm"
                aria-label="放大"
                onClick={() => zoomIn()}
              >
                <Plus size={16} />
              </ActionIcon>
              <span className="canvas-controls__divider" />
              <ActionIcon
                type="button"
                variant="subtle"
                color="gray"
                size="sm"
                aria-label="适应画面"
                onClick={() => fitView({ padding: 0.2, duration: 260 })}
              >
                <Maximize2 size={16} />
              </ActionIcon>
              <ActionIcon
                type="button"
                variant="subtle"
                color="gray"
                size="sm"
                aria-label="保存视图"
                onClick={onSaveView}
              >
                <Bookmark size={16} />
              </ActionIcon>
            </Group>
          </>
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
        {hasPendingOntologyChanges(changeset) ? (
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
