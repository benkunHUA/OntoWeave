import type { NodeCatalog, PipelineDraftResponse } from '../../../shared/types'
import { findNodeDefinition, portHasRole } from './nodeCatalog'

export type ConnectionReason = 'NODE_MISSING' | 'SELF_CONNECTION' | 'PORT_INVALID' | 'INPUT_OCCUPIED' | 'CYCLE'

export interface ConnectionCandidate {
  sourceNodeId: string
  sourcePort: string
  targetNodeId: string
  targetPort: string
}

export type ConnectionValidation =
  | { ok: true }
  | { ok: false; reason: ConnectionReason; message: string }

const MESSAGES: Record<ConnectionReason, string> = {
  NODE_MISSING: '连线两端的节点必须存在',
  SELF_CONNECTION: '节点不能连接到自身',
  PORT_INVALID: '只能从输出端口连接到输入端口',
  INPUT_OCCUPIED: '该输入端口已经连接',
  CYCLE: '连线不能形成环',
}

function fail(reason: ConnectionReason): ConnectionValidation {
  return { ok: false, reason, message: MESSAGES[reason] }
}

function reaches(
  edges: PipelineDraftResponse['definition']['edges'],
  from: string,
  target: string,
): boolean {
  const adjacency = new Map<string, string[]>()
  edges.forEach((edge) => {
    const next = adjacency.get(edge.sourceNodeId) ?? []
    next.push(edge.targetNodeId)
    adjacency.set(edge.sourceNodeId, next)
  })
  const pending = [from]
  const seen = new Set<string>()
  while (pending.length) {
    const current = pending.pop()
    if (!current || seen.has(current)) continue
    if (current === target) return true
    seen.add(current)
    pending.push(...(adjacency.get(current) ?? []))
  }
  return false
}

export function validateConnection(
  draft: PipelineDraftResponse,
  candidate: ConnectionCandidate,
  catalog: NodeCatalog | null,
): ConnectionValidation {
  const { nodes, edges } = draft.definition
  const source = nodes.find((node) => node.id === candidate.sourceNodeId)
  const target = nodes.find((node) => node.id === candidate.targetNodeId)
  if (!source || !target) return fail('NODE_MISSING')
  if (source.id === target.id) return fail('SELF_CONNECTION')

  const sourceDefinition = findNodeDefinition(catalog, source.type)
  const targetDefinition = findNodeDefinition(catalog, target.type)
  if (catalog) {
    const sourcePort = sourceDefinition?.ports.find((port) => port.id === candidate.sourcePort)
    const targetPort = targetDefinition?.ports.find((port) => port.id === candidate.targetPort)
    if (
      !sourcePort || !portHasRole(sourcePort, 'output') ||
      !targetPort || !portHasRole(targetPort, 'input')
    ) {
      return fail('PORT_INVALID')
    }
    const occupied = edges.some(
      (edge) =>
        edge.targetNodeId === candidate.targetNodeId &&
        edge.targetPort === candidate.targetPort,
    )
    if (occupied && !targetPort.multiple) return fail('INPUT_OCCUPIED')
  }

  if (reaches(edges, candidate.targetNodeId, candidate.sourceNodeId)) return fail('CYCLE')
  return { ok: true }
}
