export function formatDateTime(value: string | null | undefined, timeZone?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date)
  } catch {
    return value
  }
}

export function formatDuration(start: string | null | undefined, end: string | null | undefined): string {
  if (!start || !end) return ''
  const from = Date.parse(start)
  const to = Date.parse(end)
  if (!Number.isFinite(from) || !Number.isFinite(to)) return ''
  const seconds = Math.max(0, to - from) / 1000
  return seconds >= 10 ? `${seconds.toFixed(0)} 秒` : `${seconds.toFixed(1)} 秒`
}

export function formatCount(value: number | null | undefined): string {
  return typeof value === 'number' ? value.toLocaleString() : '—'
}

export function cellText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
