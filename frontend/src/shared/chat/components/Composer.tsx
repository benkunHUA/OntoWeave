import { ActionIcon, Button, Group, Text } from '@mantine/core'
import { Send, Square } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { TextAreaField } from '../../components/form/controls'
import { INTERRUPT_PLACEHOLDER } from '../lib/interruptCopy'
import type { PendingInterrupt } from '../lib/messageBlocks'

interface ComposerProps {
  disabled?: boolean
  isRunning: boolean
  interrupt: PendingInterrupt | null
  placeholder?: string
  onSend: (text: string) => void
  onAbort: () => void
}

/** 输入区：一句话发出去；停在中断上时，这句话就是那次中断的回答。 */
export function Composer({
  disabled = false,
  isRunning,
  interrupt,
  placeholder,
  onSend,
  onAbort,
}: ComposerProps) {
  const [input, setInput] = useState('')
  const waitingForFreeText = Boolean(interrupt?.freeText)

  function submit(event?: FormEvent) {
    event?.preventDefault()
    if (disabled || isRunning) return
    const text = input.trim()
    if (!text) return
    setInput('')
    onSend(text)
  }

  const resolvedPlaceholder = interrupt
    ? waitingForFreeText
      ? INTERRUPT_PLACEHOLDER.freeText
      : INTERRUPT_PLACEHOLDER.optionsOnly
    : (placeholder ?? '描述业务规则、对象或关系')

  return (
    <form className="composer" onSubmit={submit}>
      <TextAreaField
        className="composer__input"
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder={resolvedPlaceholder}
        autosize
        minRows={3}
        maxRows={8}
        disabled={disabled}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            submit()
          }
        }}
      />
      <Group className="composer__footer" justify="space-between" gap="sm">
        <Text component="span" size="xs" c="dimmed">
          {isRunning ? '智能体正在处理' : 'Enter 发送 · Shift + Enter 换行'}
        </Text>
        <Group className="composer__actions" gap="xs">
          {isRunning ? (
            <ActionIcon type="button" variant="default" aria-label="停止" onClick={onAbort}>
              <Square size={15} />
            </ActionIcon>
          ) : null}
          <Button
            type="submit"
            leftSection={<Send size={16} />}
            disabled={
              disabled || !input.trim() || isRunning || Boolean(interrupt && !waitingForFreeText)
            }
          >
            发送
          </Button>
        </Group>
      </Group>
    </form>
  )
}
