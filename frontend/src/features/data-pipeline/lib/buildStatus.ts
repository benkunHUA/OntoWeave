import type { PipelineBuild, PipelineBuildOutput } from '../../../shared/types'

const STAGE_LABELS: Record<string, string> = {
  resolving_inputs: '解析输入',
  submitting: '提交作业',
  running: '执行输出',
  checking_expectations: '检查数据期望',
  publishing: '发布版本',
  finished: '已完成',
}

const STATUS_LABELS: Record<string, string> = {
  queued: '排队中',
  running: '运行中',
  succeeded: '构建成功',
  failed: '构建失败',
  canceled: '已取消',
}

export function buildStageLabel(stage: string | null | undefined): string {
  if (!stage) return '等待执行'
  return STAGE_LABELS[stage.toLowerCase()] ?? stage
}

export function buildStatusLabel(status: string | null | undefined): string {
  if (!status) return '未知状态'
  return STATUS_LABELS[status.toLowerCase()] ?? status
}

function durationSeconds(start: string | null, end: string | null): number | null {
  if (!start) return null
  const started = Date.parse(start)
  const finished = end ? Date.parse(end) : Date.now()
  if (!Number.isFinite(started) || !Number.isFinite(finished)) return null
  return Math.max(0, finished - started) / 1000
}

function secondsLabel(seconds: number): string {
  return `${seconds.toFixed(seconds >= 10 ? 0 : 1)} 秒`
}

export function buildElapsedLabel(build: PipelineBuild): string | null {
  const seconds = durationSeconds(build.started_at, build.finished_at)
  return seconds === null ? null : secondsLabel(seconds)
}

export function outputElapsedLabel(output: PipelineBuildOutput): string | null {
  const seconds = durationSeconds(output.started_at, output.finished_at)
  return seconds === null ? null : secondsLabel(seconds)
}

export function outputRowCount(output: PipelineBuildOutput): number | null {
  const value = output.stats?.rowCount
  return typeof value === 'number' ? value : null
}

export interface ExpectationSummary {
  total: number
  passed: number
  failed: number
  failedRows: number
}

export function expectationSummary(output: PipelineBuildOutput): ExpectationSummary {
  const raw = output.result?.expectations
  const items = Array.isArray(raw)
    ? raw.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
    : []
  return items.reduce<ExpectationSummary>((summary, item) => {
    const passed = item.passed === true
    const failedRows = typeof item.failedRows === 'number' ? item.failedRows : 0
    summary.total += 1
    if (passed) summary.passed += 1
    else {
      summary.failed += 1
      summary.failedRows += failedRows
    }
    return summary
  }, { total: 0, passed: 0, failed: 0, failedRows: 0 })
}
