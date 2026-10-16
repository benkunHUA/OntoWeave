import { useCallback, useState } from 'react'

/** 页面右上角那条错误提示。取数失败与保存失败都汇到这里，用户只看到一个出口。 */
export function useErrorToast() {
  const [error, setError] = useState<string | null>(null)

  const report = useCallback((reason: unknown, fallback: string) => {
    setError(reason instanceof Error ? reason.message : fallback)
  }, [])

  const clear = useCallback(() => setError(null), [])

  return { error, report, setMessage: setError, clear }
}

export type ReportError = (reason: unknown, fallback: string) => void
