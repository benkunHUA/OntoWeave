import { useEffect, useRef, useState } from 'react'

interface ListControlProps {
  value: unknown
  placeholder?: string
  onChange: (value: string[]) => void
}

function listToText(value: unknown): string {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string').join(', ')
    : ''
}

function textToList(text: string): string[] {
  return text
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 字段/取值列表：用逗号分隔，保留用户正在输入的原文。 */
export function ListControl({ value, placeholder, onChange }: ListControlProps) {
  const [text, setText] = useState(() => listToText(value))
  const emittedRef = useRef(text)

  useEffect(() => {
    const incoming = listToText(value)
    if (incoming === emittedRef.current) return
    emittedRef.current = incoming
    setText(incoming)
  }, [value])

  return (
    <input
      type="text"
      value={text}
      placeholder={placeholder ?? '用逗号分隔'}
      onChange={(event) => {
        const next = event.target.value
        setText(next)
        const parsed = textToList(next)
        emittedRef.current = listToText(parsed)
        onChange(parsed)
      }}
    />
  )
}
