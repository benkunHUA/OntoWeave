import type { ReactNode } from 'react'
import { useStickToBottom } from '../hooks/useStickToBottom'

interface ChatScrollAreaProps {
  children: ReactNode
  className?: string
  /** 切换会话时重置「用户已上滚」状态，重新贴住底部。 */
  resetKey?: string | number | null
}

/** 对话滚动区的共同外壳：内容尺寸变化时自动贴底。 */
export function ChatScrollArea({ children, className, resetKey }: ChatScrollAreaProps) {
  const { containerRef, contentRef } = useStickToBottom(resetKey)
  return (
    <div
      ref={containerRef}
      className={className ? `message-scroll ${className}` : 'message-scroll'}
    >
      <div ref={contentRef} className="message-scroll__content">
        {children}
      </div>
    </div>
  )
}
