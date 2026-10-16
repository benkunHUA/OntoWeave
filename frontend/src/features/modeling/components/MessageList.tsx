import { Sparkles } from 'lucide-react'
import { useEffect, useRef } from 'react'
import type { ChatMessage, InterruptOption } from '../../../shared/types'
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
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, pendingInterruptId])

  return (
    <div className="message-scroll">
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

      <div ref={endRef} />
    </div>
  )
}
