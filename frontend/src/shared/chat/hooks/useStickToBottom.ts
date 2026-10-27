import { useLayoutEffect, useRef } from 'react'

/** 离底部这么近仍算「贴底」，避免一像素抖动就暂停自动滚动。 */
const STICK_THRESHOLD_PX = 48

/**
 * 让滚动容器贴住内容底部。
 * 监听内容尺寸而不是消息数量，流式文本或展开工具结果撑高内容时也会跟随。
 */
export function useStickToBottom(resetKey?: string | number | null) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const contentRef = useRef<HTMLDivElement | null>(null)
  const stuckRef = useRef(true)

  useLayoutEffect(() => {
    const container = containerRef.current
    const content = contentRef.current
    if (!container || !content) return

    const scrollToBottom = () => {
      container.scrollTop = Math.max(0, container.scrollHeight - container.clientHeight)
    }
    const nearBottom = () =>
      container.scrollHeight - container.scrollTop - container.clientHeight <=
      STICK_THRESHOLD_PX
    const handleScroll = () => {
      stuckRef.current = nearBottom()
    }

    stuckRef.current = true
    scrollToBottom()
    container.addEventListener('scroll', handleScroll, { passive: true })

    const observer = new ResizeObserver(() => {
      if (stuckRef.current) scrollToBottom()
    })
    observer.observe(content)
    observer.observe(container)

    return () => {
      container.removeEventListener('scroll', handleScroll)
      observer.disconnect()
    }
  }, [])

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return
    stuckRef.current = true
    container.scrollTop = Math.max(0, container.scrollHeight - container.clientHeight)
  }, [resetKey])

  return { containerRef, contentRef }
}
