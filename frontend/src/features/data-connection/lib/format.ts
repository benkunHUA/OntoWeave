export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString()
}

export function formatRows(value: number | null | undefined): string {
  if (value == null) return '—'
  return value.toLocaleString()
}

export function formatBytes(value: number | null | undefined): string {
  if (value == null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export function formatInterval(seconds: number): string {
  if (seconds % 86_400 === 0) return `每 ${seconds / 86_400} 天`
  if (seconds % 3_600 === 0) return `每 ${seconds / 3_600} 小时`
  return `每 ${seconds} 秒`
}
