import { Alert, Button, Group, Paper, Stack, Text } from '@mantine/core'
import { CircleAlert } from 'lucide-react'
import type { InterruptOption, MessageBlock } from '../../../shared/types'
import { INTERRUPT_HINT } from '../lib/interruptCopy'
import { InterruptDetails } from './InterruptDetails'

type InterruptBlock = Extract<MessageBlock, { kind: 'interrupt' }>

interface InterruptCardProps {
  block: InterruptBlock
  /** 这一块是不是当前等待回答的中断，只有它可点。 */
  pending: boolean
  /** 智能体正在跑，禁止重复提交。 */
  disabled: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 一问一答的确认卡片：问题 + 候选详情 + 选项。 */
export function InterruptCard({ block, pending, disabled, onAnswer }: InterruptCardProps) {
  return (
    <Paper
      className={`interrupt-card ${pending ? 'is-pending' : ''}`}
      withBorder
      radius="md"
      p="sm"
      bg="dark.8"
    >
      <Stack gap="xs">
        <Alert
          icon={<CircleAlert size={15} />}
          color="yellow"
          variant="light"
          p="xs"
          title={block.question}
        />
        <InterruptDetails details={block.details ?? []} />
        {block.options.length ? (
          <Group className="interrupt-options" gap="xs">
            {block.options.map((option) => (
              <Button
                key={option.label}
                type="button"
                variant="light"
                color="yellow"
                size="xs"
                onClick={() => onAnswer(option)}
                disabled={!pending || disabled}
              >
                {option.label}
              </Button>
            ))}
          </Group>
        ) : null}
        {pending ? (
          <Text size="xs" c="dimmed">
            {block.freeText ? INTERRUPT_HINT.freeText : INTERRUPT_HINT.optionsOnly}
          </Text>
        ) : null}
      </Stack>
    </Paper>
  )
}
