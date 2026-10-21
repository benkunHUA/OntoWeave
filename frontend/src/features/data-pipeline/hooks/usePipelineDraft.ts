import { useCallback, useEffect, useRef, useState } from 'react'
import {
  checkPipeline,
  getNodeCatalog,
  getPipelineDraft,
} from '../../../shared/api'
import type {
  NodeCatalog,
  PipelineCheckResult,
  PipelineDraftResponse,
} from '../../../shared/types'
import { normalizeDraftResponse } from '../lib/draft'

function reportError(reason: unknown): string {
  return reason instanceof Error ? reason.message : '读取流程失败'
}

export function usePipelineDraft(pipelineId: string | undefined) {
  const [draft, setDraftState] = useState<PipelineDraftResponse | null>(null)
  const [check, setCheck] = useState<PipelineCheckResult | null>(null)
  const [catalog, setCatalog] = useState<NodeCatalog | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const draftRef = useRef<PipelineDraftResponse | null>(null)

  const setDraft = useCallback((next: PipelineDraftResponse | null) => {
    const normalized = next ? normalizeDraftResponse(next) : null
    draftRef.current = normalized
    setDraftState(normalized)
  }, [])

  const refreshCheck = useCallback(
    async (revision: number) => {
      if (!pipelineId) return null
      const result = await checkPipeline(pipelineId, revision)
      setCheck(result)
      return result
    },
    [pipelineId],
  )

  const reload = useCallback(async () => {
    if (!pipelineId) {
      setDraft(null)
      setCheck(null)
      return null
    }
    const next = normalizeDraftResponse(await getPipelineDraft(pipelineId))
    setDraft(next)
    const checkResult = await checkPipeline(pipelineId, next.revision)
    setCheck(checkResult)
    setError(null)
    return { draft: next, check: checkResult }
  }, [pipelineId, setDraft])

  useEffect(() => {
    let active = true
    setIsLoading(true)
    void (async () => {
      try {
        const nextCatalog = await getNodeCatalog()
        if (!active) return
        setCatalog(nextCatalog)
        await reload()
      } catch (reason) {
        if (active) setError(reportError(reason))
      } finally {
        if (active) setIsLoading(false)
      }
    })()
    return () => {
      active = false
    }
  }, [reload])

  return {
    draft,
    draftRef,
    check,
    catalog,
    isLoading,
    error,
    setDraft,
    setCheck,
    refreshCheck,
    reload,
  }
}
