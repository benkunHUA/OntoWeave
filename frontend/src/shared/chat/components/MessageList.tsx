import { Sparkles } from 'lucide-react'
import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { ChatScrollArea } from './ChatScrollArea'
import { MessageBubble } from './MessageBubble'

interface MessageListProps {
  messages: ChatMessage[]
  /** 还没选会话时显示引导语；选中了但没消息就是一片留白。 */
  showEmptyState: boolean
  pendingInterruptId: string | null
  isRunning: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 滚动区：消息列表 + 空态，并在新内容到达时贴住底部。 */
export function MessageList({
  messages,
  showEmptyState,
  pendingInterruptId,
  isRunning,
  onAnswer,
}: MessageListProps) {
  const scrollKey = messages[0]?.id ?? 'empty'

  return (
    <ChatScrollArea resetKey={scrollKey}>
      {showEmptyState ? (
        <div className="chat-empty">
          <Sparkles size={26} />
          <strong>从一个业务故事开始</strong>
          <p>新建对话，或者从左侧继续已有的一张图。</p>
        </div>
      ) : null}

      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          pendingInterruptId={pendingInterruptId}
          isRunning={isRunning}
          onAnswer={onAnswer}
        />
      ))}
    </ChatScrollArea>
  )
}
