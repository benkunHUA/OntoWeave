import { Brain, ChevronRight } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

interface ThinkingBlockProps {
  text: string
  done: boolean
}

/** 思考过程：流式时展开，结束后自动折叠；用户手动展开过就不再自动收。 */
export function ThinkingBlock({ text, done }: ThinkingBlockProps) {
  const [open, setOpen] = useState(!done)
  const userToggled = useRef(false)

  useEffect(() => {
    if (done && !userToggled.current) {
      setOpen(false)
    }
  }, [done])

  return (
    <div className={`thinking-block ${done ? '' : 'is-live'}`}>
      <button
        type="button"
        className="thinking-block__head"
        aria-expanded={open}
        onClick={() => {
          userToggled.current = true
          setOpen((value) => !value)
        }}
      >
        <Brain size={13} />
        <span>{done ? '思考过程' : '正在思考…'}</span>
        <ChevronRight size={13} className={open ? 'is-open' : ''} />
      </button>
      {open ? <div className="thinking-block__body">{text}</div> : null}
    </div>
  )
}
