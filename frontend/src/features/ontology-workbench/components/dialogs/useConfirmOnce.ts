import { useEffect, useRef } from 'react'

/** 确认按钮在父级状态落地前也会被连点，这里用同步锁挡住同一轮重复提交。 */
export function useConfirmOnce(
  isSubmitting: boolean,
  error: string | null | undefined,
  onConfirm: () => void,
): () => void {
  const locked = useRef(false)

  useEffect(() => {
    if (!isSubmitting && error) locked.current = false
  }, [error, isSubmitting])

  return () => {
    if (isSubmitting || locked.current) return
    locked.current = true
    onConfirm()
  }
}
