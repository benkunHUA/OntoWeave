import type { ChatMessage, EvidenceGraph } from '../../../shared/types'
import { isNoticeMessage } from '../../../shared/chat/lib/messageBlocks'

export type {
  EvidenceEdge,
  EvidenceGraph,
  EvidenceNode,
  EvidenceObjectType,
  EvidenceRelation,
} from '../../../shared/types'

const ACTION_CONFIRMATION_KIND = 'action_confirmation'
const ACTION_EXECUTION_TOOL = 'execute_action'

/** 证据图由后端在工具执行时采集并落成独立块，前端不再从工具结果反推。 */
export function evidenceGraphFromMessage(message: ChatMessage): EvidenceGraph | null {
  const block = message.blocks.find((item) => item.kind === 'evidence')
  return block?.kind === 'evidence' ? block.graph : null
}

function hasActionConfirmation(message: ChatMessage): boolean {
  return message.blocks.some(
    (block) =>
      block.kind === 'interrupt' && block.confirmation?.kind === ACTION_CONFIRMATION_KIND,
  )
}

function hasActionExecution(message: ChatMessage): boolean {
  return message.blocks.some(
    (block) => block.kind === 'tool' && block.name === ACTION_EXECUTION_TOOL,
  )
}

function previousAssistantOfTurn(messages: ChatMessage[], index: number): ChatMessage | null {
  let foundUser = false
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const message = messages[cursor]
    if (!foundUser) {
      if (message.role === 'user') foundUser = true
      continue
    }
    if (message.role === 'assistant' && !isNoticeMessage(message.blocks)) return message
  }
  return null
}

/** 动作预览和执行结果都属于动作回合，只展示变更信息，不展示查询实例图。 */
export function isActionTurnMessage(messages: ChatMessage[], index: number): boolean {
  const message = messages[index]
  if (!message || message.role !== 'assistant') return false
  if (hasActionConfirmation(message) || hasActionExecution(message)) return true
  const previousAssistant = previousAssistantOfTurn(messages, index)
  return previousAssistant !== null && hasActionConfirmation(previousAssistant)
}
