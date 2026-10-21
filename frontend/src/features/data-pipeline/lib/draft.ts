import type {
  PipelineDefinition,
  PipelineDraftResponse,
  PipelineEdge,
  PipelineNode,
  PipelineNodePosition,
  PipelineAgentState,
  StateUpdateEvent,
} from '../../../shared/types'

function position(value: unknown): PipelineNodePosition {
  if (!value || typeof value !== 'object') return { x: 0, y: 0 }
  const source = value as Record<string, unknown>
  const x = typeof source.x === 'number' ? source.x : 0
  const y = typeof source.y === 'number' ? source.y : 0
  return { x, y }
}

function normalizeNode(value: unknown): PipelineNode | null {
  if (!value || typeof value !== 'object') return null
  const node = value as Record<string, unknown>
  if (typeof node.id !== 'string' || typeof node.type !== 'string') return null
  return {
    id: node.id,
    type: node.type,
    name: typeof node.name === 'string' ? node.name : node.type,
    config:
      node.config && typeof node.config === 'object'
        ? { ...(node.config as Record<string, unknown>) }
        : {},
    position: position(node.position),
  }
}

function normalizeEdge(value: unknown): PipelineEdge | null {
  if (!value || typeof value !== 'object') return null
  const edge = value as Record<string, unknown>
  if (
    typeof edge.id !== 'string' ||
    typeof edge.sourceNodeId !== 'string' ||
    typeof edge.sourcePort !== 'string' ||
    typeof edge.targetNodeId !== 'string' ||
    typeof edge.targetPort !== 'string'
  ) {
    return null
  }
  return {
    id: edge.id,
    sourceNodeId: edge.sourceNodeId,
    sourcePort: edge.sourcePort,
    targetNodeId: edge.targetNodeId,
    targetPort: edge.targetPort,
  }
}

/** 浏览器草稿响应保持外层 snake_case；definition 内部只做防御性归一化。 */
export function normalizeDraftResponse(
  response: PipelineDraftResponse,
): PipelineDraftResponse {
  const raw = (response.definition ?? {}) as Partial<PipelineDefinition>
  const nodes = Array.isArray(raw.nodes)
    ? raw.nodes.map(normalizeNode).filter((node): node is PipelineNode => node !== null)
    : []
  const edges = Array.isArray(raw.edges)
    ? raw.edges.map(normalizeEdge).filter((edge): edge is PipelineEdge => edge !== null)
    : []
  return {
    pipeline_id: response.pipeline_id,
    revision: response.revision,
    definition: { schemaVersion: raw.schemaVersion ?? 1, nodes, edges },
  }
}

export function pipelineStateFromEvent(event: StateUpdateEvent): PipelineAgentState | null {
  if (!event.pipelineId) return null
  return {
    pipelineId: event.pipelineId,
    draftRevision: event.draftRevision ?? null,
    check: event.check ?? null,
    loadError: event.loadError ?? null,
  }
}
