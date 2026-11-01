import { CircleAlert, ChevronRight, LoaderCircle } from 'lucide-react'
import { useId, useState } from 'react'
import type { ExecutionTraceData, ToolBlock } from '../../lib/executionTrace'
import { executionTraceHeading } from '../../lib/executionTrace'
import { EXECUTION_TRACE_COPY } from '../../lib/executionTraceCopy'
import { ExecutionReasoning } from './ExecutionReasoning'
import { ExecutionStepDetail } from './ExecutionStepDetail'
import { ExecutionStepRow } from './ExecutionStepRow'

interface ExecutionTraceProps {
  trace: ExecutionTraceData
  label?: string
  active?: boolean
}

export function ExecutionTrace({
  trace,
  label = EXECUTION_TRACE_COPY.defaultLabel,
  active = false,
}: ExecutionTraceProps) {
  const [manualOpen, setManualOpen] = useState<boolean | null>(null)
  const [detail, setDetail] = useState<ToolBlock | null>(null)
  const bodyId = useId()
  const live = trace.running || active
  const heading = executionTraceHeading(trace, label, live)
  const open = live || (manualOpen ?? false)

  return (
    <section
      className="execution-trace"
      data-status={live ? 'running' : trace.failed ? 'error' : 'ok'}
      aria-label={heading}
    >
      <button
        type="button"
        className={`execution-trace__head ${active ? 'is-locked' : ''}`}
        aria-expanded={open}
        aria-controls={bodyId}
        aria-disabled={active}
        onClick={() => {
          if (active) return
          setManualOpen(!open)
        }}
      >
        {live ? (
          <LoaderCircle size={12} className="execution-trace__status is-spinning" aria-hidden="true" />
        ) : trace.failed ? (
          <CircleAlert size={12} className="execution-trace__status" aria-hidden="true" />
        ) : null}
        <span className="execution-trace__heading">{heading}</span>
        {active ? null : (
          <ChevronRight
            size={12}
            className={`execution-trace__chevron ${open ? 'is-open' : ''}`}
            aria-hidden="true"
          />
        )}
      </button>

      {open ? (
        <div id={bodyId} className="execution-trace__body">
          <div className="execution-trace__timeline">
            {trace.reasoning.length ? <ExecutionReasoning blocks={trace.reasoning} /> : null}
            {trace.tools.map((block) => (
              <ExecutionStepRow key={block.id} block={block} onOpen={setDetail} />
            ))}
          </div>
        </div>
      ) : null}

      {detail ? <ExecutionStepDetail block={detail} onClose={() => setDetail(null)} /> : null}
    </section>
  )
}
