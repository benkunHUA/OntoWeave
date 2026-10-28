import { Group, Paper, Text, UnstyledButton } from '@mantine/core'
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
    <Paper className={`thinking-block ${done ? '' : 'is-live'}`} withBorder radius="md" p="xs">
      <UnstyledButton
        type="button"
        className="thinking-block__head"
        aria-expanded={open}
        onClick={() => {
          userToggled.current = true
          setOpen((value) => !value)
        }}
      >
        <Group gap={6} wrap="nowrap" justify="space-between">
          <Group gap={6} wrap="nowrap">
            <Brain size={13} />
            <Text component="span" size="xs" fw={600}>
              {done ? '思考过程' : '正在思考…'}
            </Text>
          </Group>
          <ChevronRight size={13} className={open ? 'is-open' : ''} />
        </Group>
      </UnstyledButton>
      {open ? <Text className="thinking-block__body" size="xs">{text}</Text> : null}
    </Paper>
  )
}
