import dagre from '@dagrejs/dagre'
import { MarkerType, type Edge, type Node } from '@xyflow/react'
import type {
  NodeCatalog,
  PipelineDraftResponse,
  PipelineNode,
  PipelineNodePort,
  PipelineNodePosition,
} from '../../../shared/types'
import { NODE_GAP_X, NODE_GAP_Y, NODE_SIZE } from './constants'
import {
  findNodeDefinition,
  inputPorts,
  nodeCategory,
  outputPorts,
} from './nodeCatalog'

export interface PipelineFlowNodeData extends Record<string, unknown> {
  label: string
  nodeType: string
  category: string
  node: PipelineNode
  inputs: PipelineNodePort[]
  outputs: PipelineNodePort[]
}

export type PipelineFlowNode = Node<PipelineFlowNodeData, 'pipeline'>

export type NodeSize = { width: number; height: number }

export function nodeSize(category: string): NodeSize {
  return NODE_SIZE[category as keyof typeof NODE_SIZE] ?? NODE_SIZE.default
}

function addEdgePort(
  ports: PipelineNodePort[],
  id: string | null,
  role: PipelineNodePort['role'],
): void {
  if (!id || ports.some((port) => port.id === id)) return
  ports.push({ id, role, data_type: 'table', required: false, multiple: false })
}

function nodePorts(
  node: PipelineNode,
  definition: ReturnType<typeof findNodeDefinition>,
  edges: PipelineDraftResponse['definition']['edges'],
): { inputs: PipelineNodePort[]; outputs: PipelineNodePort[] } {
  const inputs = inputPorts(definition)
  const outputs = outputPorts(definition)
  edges.forEach((edge) => {
    if (edge.targetNodeId === node.id) addEdgePort(inputs, edge.targetPort, 'input')
    if (edge.sourceNodeId === node.id) addEdgePort(outputs, edge.sourcePort, 'output')
  })
  return { inputs, outputs }
}

export function draftToFlow(
  draft: PipelineDraftResponse,
  catalog: NodeCatalog | null,
  selectedNodeId: string | null,
): { nodes: PipelineFlowNode[]; edges: Edge[] } {
  const draftEdges = draft.definition.edges
  const nodes = draft.definition.nodes.map((node) => {
    const definition = findNodeDefinition(catalog, node.type)
    const category = nodeCategory(definition, node.type)
    const ports = nodePorts(node, definition, draftEdges)
    const size = nodeSize(category)
    return {
      id: node.id,
      type: 'pipeline' as const,
      position: { ...node.position },
      selected: node.id === selectedNodeId,
      style: { width: size.width, height: size.height },
      data: {
        label: node.name,
        nodeType: definition?.label ?? node.type,
        category,
        node: { ...node, config: { ...node.config }, position: { ...node.position } },
        inputs: ports.inputs,
        outputs: ports.outputs,
      },
    }
  })
  const edges = draftEdges.map((edge) => ({
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
  catalog: NodeCatalog | null,
): Array<{ node_id: string } & PipelineNodePosition> {
  const graph = new dagre.graphlib.Graph()
  graph.setDefaultEdgeLabel(() => ({}))
  graph.setGraph({ rankdir: 'LR', nodesep: NODE_GAP_Y, ranksep: NODE_GAP_X })
  draft.definition.nodes.forEach((node) => {
    const definition = findNodeDefinition(catalog, node.type)
    graph.setNode(node.id, nodeSize(nodeCategory(definition, node.type)))
  })
  draft.definition.edges.forEach((edge) => graph.setEdge(edge.sourceNodeId, edge.targetNodeId))
  dagre.layout(graph)
  return draft.definition.nodes.map((node) => {
    const definition = findNodeDefinition(catalog, node.type)
    const size = nodeSize(nodeCategory(definition, node.type))
    const placed = graph.node(node.id) as { x?: number; y?: number } | undefined
    if (!placed || typeof placed.x !== 'number' || typeof placed.y !== 'number') {
      return { node_id: node.id, ...node.position }
    }
    return {
      node_id: node.id,
      x: placed.x - size.width / 2,
      y: placed.y - size.height / 2,
    }
  })
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
