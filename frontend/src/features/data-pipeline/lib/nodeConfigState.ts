import type { JsonObject, PipelineDraftResponse, PipelineNode } from '../../../shared/types'

export interface NodeConfigDrawerState {
  nodeId: string
  name: string
  config: JsonObject
  dirty: boolean
}
export function drawerStateFor(node: PipelineNode): NodeConfigDrawerState {
  return {
    nodeId: node.id,
    name: node.name,
    config: { ...node.config },
    dirty: false,
  }
}

export function resetForNode(
  state: NodeConfigDrawerState,
  node: PipelineNode,
): NodeConfigDrawerState {
  if (state.nodeId === node.id) return state
  return drawerStateFor(node)
}

export function syncExternalConfig(
  state: NodeConfigDrawerState,
  node: PipelineNode,
): NodeConfigDrawerState {
  if (state.dirty || (state.config === node.config && state.name === node.name)) return state
  return { ...state, name: node.name, config: { ...node.config } }
}

export function applyDraftResponse(
  state: NodeConfigDrawerState,
  nodeId: string,
  draft: PipelineDraftResponse,
): NodeConfigDrawerState {
  const updated = draft.definition.nodes.find((node) => node.id === nodeId)
  if (!updated) return state
  return {
    ...state,
    config: { ...state.config, ...updated.config },
  }
}
