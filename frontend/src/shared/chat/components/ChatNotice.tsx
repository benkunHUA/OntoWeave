import { Group, Text } from '@mantine/core'
import { Info } from 'lucide-react'
import type { MessageBlock } from '../../../shared/types'

type NoticeBlock = Extract<MessageBlock, { kind: 'notice' }>

/** 系统提示：居中、低调，和用户 / 助手的对话内容区分开。 */
export function ChatNotice({ block }: { block: NoticeBlock }) {
  return (
    <Group className="chat-notice" gap={6} justify="center" wrap="nowrap">
      <Info size={13} />
      <Text component="span" size="xs">{block.text}</Text>
    </Group>
  )
}
