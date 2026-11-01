import type { ChatMessage, EvidenceGraph } from '../../../shared/types'

export type {
  EvidenceEdge,
  EvidenceGraph,
  EvidenceNode,
  EvidenceObjectType,
  EvidenceRelation,
} from '../../../shared/types'

/** 证据图由后端在工具执行时采集并落成独立块，前端不再从工具结果反推。 */
export function evidenceGraphFromMessage(message: ChatMessage): EvidenceGraph | null {
  const block = message.blocks.find((item) => item.kind === 'evidence')
  return block?.kind === 'evidence' ? block.graph : null
}
