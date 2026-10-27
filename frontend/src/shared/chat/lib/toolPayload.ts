/** 工具参数与结果的可读展示：结构化 JSON 一律转成缩进格式。 */

function isStructured(value: unknown): boolean {
  return typeof value === 'object' && value !== null
}

function stringifyPretty(value: unknown): string | null {
  try {
    return JSON.stringify(value, null, 2) ?? null
  } catch {
    return null
  }
}

/** 字符串先尝试按 JSON 解析；对象/数组直接格式化，普通文本原样保留。 */
export function formatToolPayload(value: unknown): string {
  if (value === null || value === undefined) return ''

  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return ''
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return value

    try {
      const parsed: unknown = JSON.parse(trimmed)
      return isStructured(parsed) ? stringifyPretty(parsed) ?? value : value
    } catch {
      return value
    }
  }

  if (typeof value === 'object') return stringifyPretty(value) ?? String(value)
  return String(value)
}

/** 参数为空对象时不显示「参数」区。 */
export function formatToolArgs(args: Record<string, unknown>): string {
  return Object.keys(args).length ? formatToolPayload(args) : ''
}
