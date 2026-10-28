import type { ChatMessage, MessageBlock } from '../../../shared/types'

export interface QueryEvidenceItem {
  objectTypeId: string
  objectTypeName: string | null
  primaryKey: string
  title: string | null
  properties: Record<string, unknown>
  propertyNames: Record<string, string>
  indexVersionId: string | null
}

interface QueryObjectsResultLike {
  items?: unknown
  index_version_id?: unknown
  indexVersionId?: unknown
}

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>

/** 从 query_objects 工具结果里提取证据；解析失败或没有对象时返回空。 */
export function evidenceFromQueryObjectsResult(
  result: string | null | undefined,
): QueryEvidenceItem[] {
  if (!result) return []
  try {
    const payload = JSON.parse(result) as QueryObjectsResultLike
    const indexVersionId = stringValue(payload.index_version_id ?? payload.indexVersionId)
    return normalizeItems(payload.items, indexVersionId)
  } catch {
    return []
  }
}

/** 一条助手消息里最后一次带对象的查询，用于紧贴最终回答展示。 */
export function evidenceFromMessage(message: ChatMessage): QueryEvidenceItem[] {
  const blocks = queryBlocks(message.blocks)
  for (let index = blocks.length - 1; index >= 0; index -= 1) {
    const evidence = evidenceFromQueryObjectsResult(blocks[index].result)
    if (evidence.length) return evidence
  }
  return []
}

/** 整个会话最近一次带对象的查询结果。 */
export function latestEvidence(messages: ChatMessage[]): QueryEvidenceItem[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const evidence = evidenceFromMessage(messages[index])
    if (evidence.length) return evidence
  }
  return []
}

function queryBlocks(blocks: MessageBlock[]): ToolBlock[] {
  return blocks.filter(
    (block): block is ToolBlock => block.kind === 'tool' && block.name === 'query_objects',
  )
}

function normalizeItems(value: unknown, indexVersionId: string | null): QueryEvidenceItem[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const record = item as Record<string, unknown>
    const primaryKey = stringValue(record.primary_key ?? record.primaryKey)
    if (!primaryKey) return []
    return [
      {
        objectTypeId: stringValue(record.object_type_id ?? record.objectTypeId) ?? '',
        objectTypeName: stringValue(record.object_type_name ?? record.objectTypeName),
        primaryKey,
        title: stringValue(record.title),
        properties:
          record.properties && typeof record.properties === 'object'
            ? (record.properties as Record<string, unknown>)
            : {},
        propertyNames: stringRecord(record.property_names ?? record.propertyNames),
        indexVersionId: stringValue(record.index_version_id ?? record.indexVersionId) ?? indexVersionId,
      },
    ]
  })
}

function stringValue(value: unknown): string | null {
  if (typeof value === 'string') return value
  if (typeof value === 'number') return String(value)
  return null
}

function stringRecord(value: unknown): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  )
}
