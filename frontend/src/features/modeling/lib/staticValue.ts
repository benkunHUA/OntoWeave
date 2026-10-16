/**
 * 固定值（静态值）的文本 ↔ 类型转换。
 *
 * 界面上的固定值是文本输入，后端却按数据类型收具体类型（整数要 int、布尔要 bool、
 * 日期要 ISO 字符串、枚举要候选值之一），转换与判错都放这里，赋值与提交校验共用一份。
 */

import { ENUM_DATATYPE } from './datatypeOptions'

export interface StaticValueSpec {
  datatype: string
  /** 目标属性是多值时，固定值按逗号分隔成列表。 */
  isArray?: boolean
  enumValues?: string[] | null
}

export type StaticValueParse =
  | { ok: true; value: unknown }
  | { ok: false; error: string }

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const TIMESTAMP_PATTERN =
  /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?([Zz]|[+-]\d{2}:?\d{2})?$/

export function parseStaticValue(text: string, spec: StaticValueSpec): StaticValueParse {
  if (spec.isArray) {
    const items = text.split(',').map((item) => item.trim())
    if (!items.length || items.some((item) => !item)) {
      return { ok: false, error: '多值固定值用逗号分隔，别留空项' }
    }
    const parsed: unknown[] = []
    for (const item of items) {
      const one = parseScalar(item, spec)
      if (!one.ok) return one
      parsed.push(one.value)
    }
    return { ok: true, value: parsed }
  }
  return parseScalar(text.trim(), spec)
}

/** 库里读出来的静态值再显示回输入框。 */
export function staticValueText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map((item) => String(item)).join(', ')
  return String(value)
}

function parseScalar(text: string, spec: StaticValueSpec): StaticValueParse {
  if (!text) return { ok: false, error: '固定值不能为空' }
  switch (spec.datatype) {
    case 'text':
      return { ok: true, value: text }
    case ENUM_DATATYPE: {
      const allowed = spec.enumValues ?? []
      if (!allowed.includes(text)) {
        return { ok: false, error: `固定值要是候选值之一：${allowed.join(' / ')}` }
      }
      return { ok: true, value: text }
    }
    case 'integer': {
      if (!/^-?\d+$/.test(text)) return { ok: false, error: '整数请只填数字' }
      return { ok: true, value: Number.parseInt(text, 10) }
    }
    case 'decimal': {
      const value = Number(text)
      if (!text.trim() || Number.isNaN(value)) return { ok: false, error: '小数请填数字' }
      return { ok: true, value }
    }
    case 'boolean': {
      if (text === 'true') return { ok: true, value: true }
      if (text === 'false') return { ok: true, value: false }
      return { ok: false, error: '布尔请填 true 或 false' }
    }
    case 'date': {
      if (!DATE_PATTERN.test(text) || Number.isNaN(Date.parse(text))) {
        return { ok: false, error: '日期请用 2026-01-31 这种格式' }
      }
      return { ok: true, value: text }
    }
    case 'timestamp': {
      if (!TIMESTAMP_PATTERN.test(text) || Number.isNaN(Date.parse(text))) {
        return { ok: false, error: '时间请用 2026-01-31T09:30 这种格式' }
      }
      return { ok: true, value: text }
    }
    default:
      return { ok: true, value: text }
  }
}
