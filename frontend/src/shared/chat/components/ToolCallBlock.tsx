import { ChevronRight, CircleAlert, CircleCheck, LoaderCircle, Wrench } from 'lucide-react'
import { useState } from 'react'
import type { MessageBlock } from '../../../shared/types'
import { subagentLabel, toolLabel } from '../lib/toolLabels'
import { formatToolArgs, formatToolPayload } from '../lib/toolPayload'

type ToolBlock = Extract<MessageBlock, { kind: 'tool' }>

/** 工具调用：头部是动作与状态，参数和结果按需展开。 */
export function ToolCallBlock({ block }: { block: ToolBlock }) {
  const [open, setOpen] = useState(false)
  const args = formatToolArgs(block.args)
  const result = formatToolPayload(block.result)
  const hasDetail = Boolean(args) || Boolean(result)
  // 子智能体内部的步骤：挂在委派行下面，标出是哪个子智能体在动。
  const agent = block.agent ? subagentLabel(block.agent) : null

  return (
    <div
      className={`tool-block tool-block--${block.status} ${agent ? 'tool-block--subagent' : ''}`}
    >
      <button
        type="button"
        className="tool-block__head"
        title={block.name}
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
        {agent ? <span className="tool-block__agent">{agent}</span> : null}
        <span className="tool-block__label">{toolLabel(block.name, block.args)}</span>
        {block.summary ? (
          <span className="tool-block__summary" title={block.summary}>
            {block.summary}
          </span>
        ) : null}
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
          {result ? (
            <>
              <span className="tool-block__caption">
                <Wrench size={11} />
                结果{block.truncated ? '（已截断）' : ''}
              </span>
              <pre>{result}</pre>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
