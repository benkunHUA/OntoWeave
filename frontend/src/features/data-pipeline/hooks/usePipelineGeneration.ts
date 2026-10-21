import { useCallback, useRef } from 'react'

/** 流程切换代次：异步响应只有代次与流程 id 都匹配时才能落到当前工作区。 */
export function usePipelineGeneration(pipelineId: string | undefined) {
  const stateRef = useRef({ id: pipelineId, token: 0 })
  if (stateRef.current.id !== pipelineId) {
    stateRef.current = { id: pipelineId, token: stateRef.current.token + 1 }
  }
  const token = stateRef.current.token
  const currentId = useCallback(() => stateRef.current.id, [])
  const isCurrent = useCallback((targetId: string | undefined, targetToken: number) =>
    stateRef.current.id === targetId && stateRef.current.token === targetToken, [])
  return { token, currentId, isCurrent }
}
