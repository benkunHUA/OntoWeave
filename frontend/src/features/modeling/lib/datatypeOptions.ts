/**
 * 属性数据类型的共享词表：对象字段与动作参数用的是同一套类型，
 * 下拉选项、标签与枚举候选值的判断都放这里，两处各写一份迟早会走样。
 */

/** 数据类型下拉的候选项：值用后端约定的英文，界面显示中文。 */
export interface DatatypeOption {
  value: string
  label: string
}

export const DATATYPE_OPTIONS: DatatypeOption[] = [
  { value: 'text', label: '文本' },
  { value: 'integer', label: '整数' },
  { value: 'decimal', label: '小数' },
  { value: 'boolean', label: '布尔' },
  { value: 'date', label: '日期' },
  { value: 'timestamp', label: '日期时间' },
  { value: 'enum', label: '枚举' },
]

/** 枚举类型：候选值放在使用方自己身上（属性、动作参数各存一份）。 */
export const ENUM_DATATYPE = 'enum'

/** 一个枚举至少要两个候选值，否则等同于常量。 */
export const MIN_ENUM_VALUES = 2

/** 老数据里可能有非标准类型，兜底把它也放进下拉，别在编辑时悄悄改掉。 */
export function datatypeOptions(current: string): DatatypeOption[] {
  if (DATATYPE_OPTIONS.some((option) => option.value === current)) {
    return DATATYPE_OPTIONS
  }
  return [...DATATYPE_OPTIONS, { value: current, label: current }]
}

export function datatypeLabel(datatype: string): string {
  return DATATYPE_OPTIONS.find((option) => option.value === datatype)?.label ?? datatype
}

/** 能比大小的数据类型；其余类型只能用等值 / 判空。 */
export const ORDERED_DATATYPES: readonly string[] = [
  'integer',
  'decimal',
  'date',
  'timestamp',
]

function trimmedValues(values: string[]): string[] {
  return values.map((value) => value.trim())
}

/** 枚举候选值能不能用：去掉空白后至少两个、且互不重复。 */
export function hasUsableEnumValues(values: string[]): boolean {
  const cleaned = trimmedValues(values).filter(Boolean)
  return cleaned.length >= MIN_ENUM_VALUES && new Set(cleaned).size === cleaned.length
}

/** 枚举候选值落库前的整理：候选值只在 enum 类型下有意义。 */
export function enumValuesFor(datatype: string, values: string[]): string[] | null {
  return datatype === ENUM_DATATYPE ? trimmedValues(values) : null
}

export function sameEnumValues(next: string[] | null, current: string[] | null): boolean {
  if (next === null || current === null) return next === current
  return next.length === current.length && next.every((value, index) => value === current[index])
}
