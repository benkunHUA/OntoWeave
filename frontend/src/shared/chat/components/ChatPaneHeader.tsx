import { Badge, Group, Text, Title, type MantineColor } from '@mantine/core'
import type { ReactNode } from 'react'

interface ChatPaneHeaderProps {
  eyebrow: string
  title: string
  status: string
  statusColor?: MantineColor
  statusIcon?: ReactNode
  titleAdornment?: ReactNode
}

/** 各聊天面板共用头部：眉题、标题、可选标题动作与运行状态。 */
export function ChatPaneHeader({
  eyebrow,
  title,
  status,
  statusColor = 'teal',
  statusIcon,
  titleAdornment,
}: ChatPaneHeaderProps) {
  return (
    <header className="chat-header">
      <div>
        <Text className="eyebrow" size="xs" c="dimmed">{eyebrow}</Text>
        <Group className="chat-title-row" gap="xs" wrap="nowrap">
          <Title order={1}>{title}</Title>
          {titleAdornment}
        </Group>
      </div>
      <Badge color={statusColor} variant="light" leftSection={statusIcon}>{status}</Badge>
    </header>
  )
}
