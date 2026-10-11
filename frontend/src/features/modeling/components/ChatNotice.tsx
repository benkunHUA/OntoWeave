import { Info } from 'lucide-react'
import type { MessageBlock } from '../../../shared/types'

type NoticeBlock = Extract<MessageBlock, { kind: 'notice' }>

/** 系统提示：居中、低调，和用户 / 助手的对话内容区分开。 */
export function ChatNotice({ block }: { block: NoticeBlock }) {
  return (
    <p className="chat-notice">
      <Info size={13} />
      <span>{block.text}</span>
    </p>
  )
}
