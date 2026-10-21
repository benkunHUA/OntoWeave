import { useEffect, useRef, useState } from 'react'

interface JsonControlProps {
  value: unknown
  hint?: string
  onChange: (value: unknown) => void
}

function stringify(value: unknown): string {
  if (value === undefined) return '{}'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return '{}'
  }
}

/** 复杂结构化配置的 JSON 编辑区：合法 JSON 才提交，结构问题交给 Java 检查。 */
export function JsonControl({ value, hint, onChange }: JsonControlProps) {
  const [text, setText] = useState(() => stringify(value))
  const [error, setError] = useState<string | null>(null)
  const emittedRef = useRef(text)

  useEffect(() => {
    const incoming = stringify(value)
    if (incoming === emittedRef.current) return
    emittedRef.current = incoming
    setText(incoming)
  }, [value])

  return (
    <div className="pipeline-json-field">
      <textarea
        rows={7}
        value={text}
        spellCheck={false}
        onChange={(event) => {
          const next = event.target.value
          setText(next)
          try {
            const parsed = JSON.parse(next) as unknown
            emittedRef.current = stringify(parsed)
            setError(null)
            onChange(parsed)
          } catch {
            setError('JSON 格式不合法，当前修改尚未保存')
          }
        }}
      />
      {hint ? <p className="pipeline-field-hint">{hint}</p> : null}
      {error ? <p className="pipeline-field-error">{error}</p> : null}
    </div>
  )
}
