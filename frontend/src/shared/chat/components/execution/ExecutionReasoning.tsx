import { Brain, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import type { ReasoningBlock } from '../../lib/executionTrace'
import { reasoningSummary } from '../../lib/executionTrace'

export function ExecutionReasoning({ blocks }: { blocks: ReasoningBlock[] }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="execution-reasoning">
      <button
        type="button"
        className="execution-reasoning__head"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Brain size={12} aria-hidden="true" />
        <span>{reasoningSummary(blocks.length)}</span>
        <ChevronRight size={12} className={open ? 'is-open' : ''} aria-hidden="true" />
      </button>
      {open ? (
        <div className="execution-reasoning__body">
          {blocks.map((block) => (
            <p key={block.id}>{block.text}</p>
          ))}
        </div>
      ) : null}
    </div>
  )
}
