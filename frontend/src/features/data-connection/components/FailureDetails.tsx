import { ChevronRight } from 'lucide-react'
import { useId, useState } from 'react'

interface FailureDetailsProps {
  message: string
  code?: string | null
}

export function FailureDetails({ message, code }: FailureDetailsProps) {
  const [open, setOpen] = useState(false)
  const contentId = useId()

  return (
    <div className="failure-details">
      <button
        type="button"
        className="failure-details__toggle"
        aria-controls={contentId}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <ChevronRight size={12} className={open ? 'is-open' : ''} />
        失败详情
        {code ? <span className="failure-details__code">{code}</span> : null}
      </button>
      {open ? (
        <pre id={contentId} className="failure-details__content">{message}</pre>
      ) : null}
    </div>
  )
}
