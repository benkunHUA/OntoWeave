import dagre from '@dagrejs/dagre'
import { MarkerType, type Edge, type Node } from '@xyflow/react'
import type {
  NodeCatalog,
  PipelineDraftResponse,
  PipelineNode,
  PipelineNodePort,
  PipelineNodePosition,
} from '../../../shared/types'
import { NODE_GAP_X, NODE_GAP_Y, NODE_HEIGHT, NODE_WIDTH } from './constants'
import { findNodeDefinition, inputPorts, outputPorts } from './nodeCatalog'

export interface PipelineFlowNodeData extends Record<string, unknown> {
  label: string
  nodeType: string
  node: PipelineNode
  inputs: PipelineNodePort[]
  outputs: PipelineNodePort[]
}

export type PipelineFlowNode = Node<PipelineFlowNodeData, 'pipeline'>

export function draftToFlow(
  draft: PipelineDraftResponse,
  catalog: NodeCatalog | null,
  selectedNodeId: string | null,
): { nodes: PipelineFlowNode[]; edges: Edge[] } {
  const nodes = draft.definition.nodes.map((node) => {
    const definition = findNodeDefinition(catalog, node.type)
    return {
      id: node.id,
      type: 'pipeline' as const,
      position: { ...node.position },
      selected: node.id === selectedNodeId,
      style: { width: NODE_WIDTH, height: NODE_HEIGHT },
      data: {
        label: node.name,
        nodeType: definition?.label ?? node.type,
        node: { ...node, config: { ...node.config }, position: { ...node.position } },
        inputs: inputPorts(definition),
        outputs: outputPorts(definition),
      },
    }
  })
  const edges = draft.definition.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeId,
    sourceHandle: edge.sourcePort,
    target: edge.targetNodeId,
    targetHandle: edge.targetPort,
    type: 'smoothstep',
    markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14 },
  }))
  return { nodes, edges }
}

export function layoutPipeline(
  draft: PipelineDraftResponse,
): Array<{ node_id: string } & PipelineNodePosition> {
  const graph = new dagre.graphlib.Graph()
  graph.setDefaultEdgeLabel(() => ({}))
  graph.setGraph({ rankdir: 'LR', nodesep: NODE_GAP_Y, ranksep: NODE_GAP_X })
  draft.definition.nodes.forEach((node) => {
    graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  })
  draft.definition.edges.forEach((edge) => graph.setEdge(edge.sourceNodeId, edge.targetNodeId))
  dagre.layout(graph)
  return draft.definition.nodes.map((node) => {
    const placed = graph.node(node.id) as { x?: number; y?: number } | undefined
    if (!placed || typeof placed.x !== 'number' || typeof placed.y !== 'number') {
      return { node_id: node.id, ...node.position }
    }
    return {
      node_id: node.id,
      x: placed.x - NODE_WIDTH / 2,
      y: placed.y - NODE_HEIGHT / 2,
    }
  })
}

/** 节点面板点击添加时的落点：空图放在固定原点，否则排在最右节点右侧。 */
export function nextNodePosition(draft: PipelineDraftResponse | null): PipelineNodePosition {
  const centerY = 160 + NODE_HEIGHT / 2
  if (!draft?.definition.nodes.length) return { x: 80 + NODE_WIDTH / 2, y: centerY }
  const right = draft.definition.nodes.reduce(
    (max, node) => Math.max(max, node.position.x),
    0,
  )
  return { x: right + NODE_WIDTH + NODE_GAP_X + NODE_WIDTH / 2, y: centerY }
}

export function changedPositionUpdates(
  draft: PipelineDraftResponse,
  moved: Array<{ id: string; position: PipelineNodePosition }>,
): Array<{ node_id: string } & PipelineNodePosition> {
  return moved.flatMap((node) => {
    const position = { x: Math.round(node.position.x), y: Math.round(node.position.y) }
    const persisted = draft.definition.nodes.find((item) => item.id === node.id)
    return positionChanged(persisted, position) ? [{ node_id: node.id, ...position }] : []
  })
}

export function positionChanged(
  node: PipelineNode | undefined,
  position: PipelineNodePosition,
): boolean {
  return !node ||
    Math.abs(node.position.x - position.x) >= 0.5 ||
    Math.abs(node.position.y - position.y) >= 0.5
}
