import type { IndexRun, RunPhase, TriggerType } from '../../../../shared/types'

export const RUN_TRIGGER_LABELS: Record<TriggerType, string> = {
  MANUAL: '手动生成',
  DATASET_PUBLISHED: '数据集发布',
  RETRY: '手动重试',
}

export const RUN_PHASE_LABELS: Record<RunPhase, string> = {
  READING: '读取',
  VALIDATING: '校验',
  WRITING: '写入',
  SWITCHING: '切换',
}

export interface RunCounts {
  written: number
  warnings: number
  skipped: number
}

interface RunDetails extends IndexRun {
  error_details?: Record<string, unknown> | null
  errorDetails?: Record<string, unknown> | null
}

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function number(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

function relationStats(stats: Record<string, unknown>): Record<string, unknown>[] {
  return Array.isArray(stats.relations) ? stats.relations.map(record) : []
}

export function runCounts(run: IndexRun): RunCounts {
  const stats = record(run.stats)
  const relations = relationStats(stats)
  const relationSkipped = relations.reduce((total, item) => total + number(item.skippedCount), 0)
  return {
    written: number(stats.objectRowCount ?? stats.rowCount),
    warnings: number(stats.objectWarningCount),
    skipped: number(stats.skippedCount) + relationSkipped,
  }
}

export function runSamples(run: IndexRun): string[] {
  const details = record((run as RunDetails).error_details ?? (run as RunDetails).errorDetails)
  const stats = record(run.stats)
  const candidates = details.samples ?? stats.samples
  if (!Array.isArray(candidates)) return []
  return candidates
    .slice(0, 20)
    .map((sample) => {
      if (typeof sample === 'string') return sample
      if (sample && typeof sample === 'object') return JSON.stringify(sample)
      return String(sample)
    })
}

export function formatRunTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}
