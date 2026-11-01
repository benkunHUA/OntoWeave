import { CircleAlert, LoaderCircle } from 'lucide-react'
import type { ToolBlock } from '../../lib/executionTrace'
import { describeExecutionStep } from '../../lib/executionTrace'
import { subagentLabel } from '../../lib/toolLabels'

interface ExecutionStepRowProps {
  block: ToolBlock
  onOpen: (block: ToolBlock) => void
}

export function ExecutionStepRow({ block, onOpen }: ExecutionStepRowProps) {
  const title = describeExecutionStep(block)
  const agent = block.agent ? subagentLabel(block.agent) : null

  return (
    <button
      type="button"
      className="execution-step"
      data-status={block.status}
      title={title}
      aria-label={`查看步骤详情：${title}`}
      onClick={() => onOpen(block)}
    >
      <span className="execution-step__dot" aria-hidden="true" />
      {block.status === 'running' ? (
        <LoaderCircle size={12} className="execution-step__status is-spinning" aria-hidden="true" />
      ) : block.status === 'error' ? (
        <CircleAlert size={12} className="execution-step__status" aria-hidden="true" />
      ) : null}
      <span className="execution-step__text">{title}</span>
      {agent ? <span className="execution-step__agent">{agent}</span> : null}
    </button>
  )
}
