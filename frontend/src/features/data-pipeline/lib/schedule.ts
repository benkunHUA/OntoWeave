import type { Pipeline } from '../../../shared/types'

const REASON_LABELS: Record<string, string> = {
  NO_CHANGE: '输入和逻辑未变化，已跳过',
  NO_ACTIVE_VERSION: '尚未成功构建，跳过',
  SCHEDULE_PAUSED: '调度已暂停，跳过待执行运行',
  PIPELINE_ARCHIVED: '流程已归档，跳过',
  BUILD_ACTIVE: '已有构建运行，等待下一轮',
}

const RUN_STATUS_LABELS: Record<string, string> = {
  pending: '等待构建空位',
  dispatched: '已派发构建',
  ignored: '已跳过',
  failed: '调度失败',
}

export function scheduleRunLabel({
  status,
  reason_code,
}: {
  status: string
  reason_code: string | null
}): string {
  if (reason_code) return REASON_LABELS[reason_code] ?? reason_code
  if (status === 'dispatched') return '已创建构建'
  if (status === 'pending') return '等待构建空位'
  return scheduleRunStatusLabel(status)
}

export function scheduleRunStatusLabel(status: string | null | undefined): string {
  if (!status) return '未知状态'
  return RUN_STATUS_LABELS[status] ?? status
}

export function triggerTypeLabel(trigger: string | null | undefined): string {
  if (!trigger) return '未知触发'
  if (trigger === 'time' || trigger === 'schedule' || trigger === 'scheduled') return '时间触发'
  if (trigger === 'manual') return '手动触发'
  return trigger
}

export function scheduleVersionHint(
  pipeline: Pipeline | null,
  draftRevision: number | null,
): string | null {
  if (!pipeline) return null
  if (!pipeline.current_version_id) return '首次构建后才能定时运行'
  if (
    pipeline.current_version_draft_revision !== null &&
    pipeline.current_version_draft_revision !== draftRevision
  ) {
    const version = pipeline.current_version_number === null
      ? ''
      : ` v${pipeline.current_version_number}`
    return `调度仍使用已发布版本${version}`
  }
  return null
}

export function scheduleStatusLabel(status: string | null | undefined): string {
  if (status === 'active') return '已启用'
  if (status === 'paused') return '已暂停'
  return status ?? '未创建'
}

export const SCHEDULE_PRESETS = [
  { id: 'hourly', label: '每小时', cron: '0 * * * *' },
  { id: 'daily', label: '每天 09:00', cron: '0 9 * * *' },
  { id: 'weekly', label: '每周一 09:00', cron: '0 9 * * 1' },
] as const

export const SCHEDULE_TIMEZONES = [
  'UTC',
  'Asia/Shanghai',
  'Asia/Tokyo',
  'America/New_York',
  'Europe/London',
] as const
