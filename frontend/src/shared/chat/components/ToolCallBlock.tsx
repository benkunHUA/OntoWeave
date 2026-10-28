import { Code, Group, Paper, Text, UnstyledButton } from '@mantine/core'
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
    <Paper
      className={`tool-block tool-block--${block.status} ${agent ? 'tool-block--subagent' : ''}`}
      withBorder
      radius="md"
      p="xs"
    >
      <UnstyledButton
        type="button"
        className="tool-block__head"
        title={block.name}
        aria-expanded={open}
        disabled={!hasDetail}
        onClick={() => setOpen((value) => !value)}
      >
        <Group gap={6} wrap="nowrap">
          {block.status === 'running' ? (
            <LoaderCircle size={13} className="is-spinning" />
          ) : block.status === 'error' ? (
            <CircleAlert size={13} />
          ) : (
            <CircleCheck size={13} />
          )}
          {agent ? <Text component="span" size="xs" c="dimmed">{agent}</Text> : null}
          <Text component="span" size="xs" fw={600}>{toolLabel(block.name, block.args)}</Text>
          {block.summary ? (
            <Text component="span" size="xs" c="dimmed" title={block.summary} truncate>
              {block.summary}
            </Text>
          ) : null}
          {block.durationMs ? <Text component="span" size="xs" c="dimmed">{block.durationMs}ms</Text> : null}
          {hasDetail ? <ChevronRight size={13} className={open ? 'is-open' : ''} /> : null}
        </Group>
      </UnstyledButton>
      {open ? (
        <div className="tool-block__body">
          {args ? (
            <>
              <Text className="tool-block__caption" size="xs" c="dimmed">参数</Text>
              <Code block>{args}</Code>
            </>
          ) : null}
          {result ? (
            <>
              <Text className="tool-block__caption" size="xs" c="dimmed">
                <Wrench size={11} />
                结果{block.truncated ? '（已截断）' : ''}
              </Text>
              <Code block>{result}</Code>
            </>
          ) : null}
        </div>
      ) : null}
    </Paper>
  )
}
