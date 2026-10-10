import { ChevronRight, CircleAlert, CircleCheck, LoaderCircle, Wrench } from 'lucide-react'
import { useState } from 'react'
import type { MessageBlock } from '../../../shared/types'
import { toolLabel } from '../lib/toolLabels'

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>

function formatArgs(args: Record<string, unknown>) {
  const entries = Object.entries(args)
  if (entries.length === 0) return ''
  return JSON.stringify(args, null, 2)
}

/** 工具调用：头部是动作与状态，参数和结果按需展开。 */
export function ToolCallBlock({ block }: { block: ToolBlock }) {
  const [open, setOpen] = useState(false)
  const args = formatArgs(block.args)
  const hasDetail = Boolean(args) || Boolean(block.result)

  return (
    <div className={`tool-block tool-block--${block.status}`}>
      <button
        type="button"
        className="tool-block__head"
        aria-expanded={open}
        disabled={!hasDetail}
        onClick={() => setOpen((value) => !value)}
      >
        {block.status === 'running' ? (
          <LoaderCircle size={13} className="is-spinning" />
        ) : block.status === 'error' ? (
          <CircleAlert size={13} />
        ) : (
          <CircleCheck size={13} />
        )}
        <span className="tool-block__label">{toolLabel(block.name)}</span>
        <code>{block.name}</code>
        {block.durationMs ? <em>{block.durationMs}ms</em> : null}
        {hasDetail ? <ChevronRight size={13} className={open ? 'is-open' : ''} /> : null}
      </button>
      {open ? (
        <div className="tool-block__body">
          {args ? (
            <>
              <span className="tool-block__caption">参数</span>
              <pre>{args}</pre>
            </>
          ) : null}
          {block.result ? (
            <>
              <span className="tool-block__caption">
                <Wrench size={11} />
                结果{block.truncated ? '（已截断）' : ''}
              </span>
              <pre>{block.result}</pre>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
