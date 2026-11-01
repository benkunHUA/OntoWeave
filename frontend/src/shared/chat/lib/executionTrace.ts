import type { MessageBlock } from '../../../shared/types'
import { EXECUTION_TRACE_COPY } from './executionTraceCopy'
import { toolLabel } from './toolLabels'

export type ReasoningBlock = Extract<MessageBlock, { kind: 'reasoning' }>
export type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>
export type TextBlock = Extract<MessageBlock, { kind: 'text' }>
export type NoticeBlock = Extract<MessageBlock, { kind: 'notice' }>
export type InterruptBlock = Extract<MessageBlock, { kind: 'interrupt' }>
export type EvidenceBlock = Extract<MessageBlock, { kind: 'evidence' }>

export interface ExecutionTraceData {
  id: string
  tools: ToolBlock[]
  reasoning: ReasoningBlock[]
  running: boolean
  failed: boolean
  durationMs: number
  stepCount: number
}

export interface MessageBlockGroups {
  traces: ExecutionTraceData[]
  text: TextBlock[]
  notices: NoticeBlock[]
  interrupts: InterruptBlock[]
  evidence: EvidenceBlock[]
}

function isExecutionBlock(block: MessageBlock): block is ReasoningBlock | ToolBlock {
  return block.kind === 'reasoning' || block.kind === 'tool'
}

export function groupExecutionTraces(blocks: MessageBlock[]): ExecutionTraceData[] {
  const traces: ExecutionTraceData[] = []
  let tools: ToolBlock[] = []
  let reasoning: ReasoningBlock[] = []
  let firstId: string | null = null

  const flush = () => {
    if (!firstId) return
    traces.push({
      id: `trace:${firstId}`,
      tools,
      reasoning,
      running:
        tools.some((tool) => tool.status === 'running') ||
        reasoning.some((item) => !item.done),
      failed: tools.some((tool) => tool.status === 'error'),
      durationMs: tools.reduce((total, tool) => total + (tool.durationMs ?? 0), 0),
      stepCount: tools.length,
    })
    tools = []
    reasoning = []
    firstId = null
  }

  for (const block of blocks) {
    if (!isExecutionBlock(block)) {
      flush()
      continue
    }
    firstId ??= block.id
    if (block.kind === 'reasoning') reasoning.push(block)
    else tools.push(block)
  }
  flush()
  return traces
}

export function orderMessageBlocks(blocks: MessageBlock[]): MessageBlockGroups {
  return {
    traces: groupExecutionTraces(blocks),
    text: blocks.filter((block): block is TextBlock => block.kind === 'text'),
    notices: blocks.filter((block): block is NoticeBlock => block.kind === 'notice'),
    interrupts: blocks.filter((block): block is InterruptBlock => block.kind === 'interrupt'),
    evidence: blocks.filter((block): block is EvidenceBlock => block.kind === 'evidence'),
  }
}

function parsePayload(value: unknown): unknown {
  if (typeof value !== 'string') return value
  const trimmed = value.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return value
  try {
    return JSON.parse(trimmed) as unknown
  } catch {
    return value
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function numberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

export function toolResultCount(result: unknown): number | null {
  const parsed = parsePayload(result)
  if (Array.isArray(parsed)) return parsed.length
  if (isRecord(parsed)) {
    const total = numberValue(parsed.total)
    if (total !== null) return total
    for (const key of ['items', 'results', 'rows', 'links', 'matches', 'objects']) {
      const value = parsed[key]
      if (Array.isArray(value)) return value.length
    }
    for (const key of ['count', 'length']) {
      const value = numberValue(parsed[key])
      if (value !== null) return value
    }
    return null
  }
  if (typeof parsed === 'string') {
    const match = parsed.match(/(\d+)\s*(?:条|个|项)/)
    return match ? Number(match[1]) : null
  }
  return null
}

function stripLimit(summary: string): string {
  return summary.replace(/，最多 \d+ 条$/, '')
}

function resultSuffix(count: number | null): string {
  return count === null ? '' : `→ ${count} 条`
}

function asText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function queryObjectTitle(block: ToolBlock, summary: string, count: number | null): string {
  const cleaned = stripLimit(summary)
  const search = cleaned.match(/^(.+?) · 搜索「(.*)」$/)
  if (search) return `查询${search[1]}「${search[2]}」${resultSuffix(count)}`

  const filters = cleaned.match(/^(.+?) · 按 (\d+) 个条件筛选$/)
  if (filters) {
    return `查询${filters[1]} · ${filters[2]} 个条件 ${resultSuffix(count)}`
  }

  const primaryKeys = cleaned.match(/^(.+?) · 按主键「(.*)」$/)
  if (primaryKeys) {
    return `读取${primaryKeys[1]} ${primaryKeys[2]} ${resultSuffix(count)}`
  }

  const keyCount = cleaned.match(/^(.+?) · 按 (\d+) 个主键$/)
  if (keyCount) {
    return `读取${keyCount[1]}（${keyCount[2]} 个主键） ${resultSuffix(count)}`
  }

  const list = cleaned.match(/^(.+?) · 读取列表$/)
  if (list) return `查询${list[1]} · 列表 ${resultSuffix(count)}`

  const q = asText(block.args.q)
  if (q) return `查询对象「${q}」${resultSuffix(count)}`
  const keys = Array.isArray(block.args.primary_keys) ? block.args.primary_keys : []
  if (keys.length === 1) return `读取对象 ${String(keys[0])} ${resultSuffix(count)}`
  if (keys.length > 1) return `读取对象（${keys.length} 个主键） ${resultSuffix(count)}`
  return `查询对象${resultSuffix(count)}`
}

function relatedObjectTitle(summary: string, count: number | null): string {
  const cleaned = stripLimit(summary)
  return cleaned ? `${cleaned} ${resultSuffix(count)}` : `沿关系查询对象${resultSuffix(count)}`
}

function graphSearchTitle(count: number | null): string {
  return count === null
    ? toolLabel('search_graphs')
    : `检索业务图 → ${count} ${EXECUTION_TRACE_COPY.candidateUnit}`
}

function graphDetailTitle(block: ToolBlock): string | null {
  const parsed = parsePayload(block.result)
  if (!isRecord(parsed)) return null
  const name = asText(parsed.name)
  const objectTypes = Array.isArray(parsed.object_types) ? parsed.object_types.length : null
  const relations = Array.isArray(parsed.relations) ? parsed.relations.length : null
  const facts: string[] = []
  if (objectTypes !== null) facts.push(`${objectTypes} ${EXECUTION_TRACE_COPY.objectTypeUnit}`)
  if (relations !== null) facts.push(`${relations} ${EXECUTION_TRACE_COPY.relationUnit}`)
  const label = name ? `读取业务图「${name}」` : toolLabel('get_graph_detail')
  return facts.length ? `${label} · ${facts.join(' / ')}` : label
}

export function describeExecutionStep(block: ToolBlock): string {
  const summary = block.summary?.trim() ?? ''
  const count = toolResultCount(block.result)

  if (block.name === 'query_objects') return queryObjectTitle(block, summary, count)
  if (block.name === 'get_related_objects') return relatedObjectTitle(summary, count)
  if (block.name === 'search_graphs') return graphSearchTitle(count)
  if (block.name === 'get_graph_detail') return graphDetailTitle(block) ?? toolLabel(block.name, block.args)

  const label = toolLabel(block.name, block.args)
  const cleaned = summary ? stripLimit(summary) : ''
  return cleaned ? `${label} · ${cleaned} ${resultSuffix(count)}` : label
}

export function formatExecutionDuration(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs <= 0) return ''
  if (durationMs < 1000) return `${Math.round(durationMs)}ms`
  const seconds = durationMs / 1000
  const value = seconds >= 10 ? Math.round(seconds).toString() : seconds.toFixed(1).replace(/\.0$/, '')
  return `${value}s`
}

export function executionTraceHeading(
  trace: ExecutionTraceData,
  label: string,
  live = trace.running,
): string {
  const duration = formatExecutionDuration(trace.durationMs)
  if (!trace.stepCount && trace.reasoning.length) {
    if (live) return EXECUTION_TRACE_COPY.thinkingLive
    return `${EXECUTION_TRACE_COPY.thinkingDone} · ${trace.reasoning.length} ${EXECUTION_TRACE_COPY.reasoningUnit}${duration ? ` · ${duration}` : ''}`
  }

  const parts = [label]
  if (live) parts.push(EXECUTION_TRACE_COPY.running)
  if (trace.failed) parts.push(EXECUTION_TRACE_COPY.failedSuffix)
  parts.push(`${trace.stepCount} ${EXECUTION_TRACE_COPY.stepUnit}`)
  if (duration) parts.push(duration)
  return parts.join(' · ')
}

export function reasoningSummary(count: number): string {
  return `${EXECUTION_TRACE_COPY.reasoningLabel} ${count} ${EXECUTION_TRACE_COPY.reasoningUnit}`
}
