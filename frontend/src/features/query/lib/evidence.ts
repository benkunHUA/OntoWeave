import type { ChatMessage, MessageBlock } from '../../../shared/types'

export interface QueryEvidenceItem {
  stepId: string
  objectTypeId: string
  primaryKey: string
  title: string | null
  properties: Record<string, unknown>
  indexVersionId: string | null
}

interface QueryAgentResultLike {
  evidence?: unknown
}

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>

/** 从 task 工具结果（QueryAgentResult JSON）里提取证据；解析失败或没有证据时返回空。 */
export function evidenceFromTaskResult(result: string | null | undefined): QueryEvidenceItem[] {
  if (!result) return []
  try {
    const payload = JSON.parse(result) as QueryAgentResultLike
    return normalizeEvidence(payload.evidence)
  } catch {
    return []
  }
}

/** 一条助手消息里最后一次带证据的 task，用于紧贴最终回答展示。 */
export function evidenceFromMessage(message: ChatMessage): QueryEvidenceItem[] {
  const blocks = taskBlocks(message.blocks)
  for (let index = blocks.length - 1; index >= 0; index -= 1) {
    const evidence = evidenceFromTaskResult(blocks[index].result)
    if (evidence.length) return evidence
  }
  return []
}

/** 整个会话最近一次带证据的 task 结果。 */
export function latestEvidence(messages: ChatMessage[]): QueryEvidenceItem[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const evidence = evidenceFromMessage(messages[index])
    if (evidence.length) return evidence
  }
  return []
}

function taskBlocks(blocks: MessageBlock[]): ToolBlock[] {
  return blocks.filter(
    (block): block is ToolBlock => block.kind === 'tool' && block.name === 'task',
  )
}

function normalizeEvidence(value: unknown): QueryEvidenceItem[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const record = item as Record<string, unknown>
    const primaryKey = stringValue(record.primary_key ?? record.primaryKey)
    if (!primaryKey) return []
    return [
      {
        stepId: stringValue(record.step_id ?? record.stepId) ?? '',
        objectTypeId: stringValue(record.object_type_id ?? record.objectTypeId) ?? '',
        primaryKey,
        title: stringValue(record.title),
        properties: record.properties && typeof record.properties === 'object'
          ? (record.properties as Record<string, unknown>)
          : {},
        indexVersionId: stringValue(record.index_version_id ?? record.indexVersionId),
      },
    ]
  })
}

function stringValue(value: unknown): string | null {
  if (typeof value === 'string') return value
  if (typeof value === 'number') return String(value)
  return null
}
