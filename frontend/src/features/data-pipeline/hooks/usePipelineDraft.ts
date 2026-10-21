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
import { usePipelineGeneration } from './usePipelineGeneration'

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
  const { token: generation, currentId, isCurrent } = usePipelineGeneration(pipelineId)

  const setDraft = useCallback((next: PipelineDraftResponse | null) => {
    const normalized = next ? normalizeDraftResponse(next) : null
    if (normalized && normalized.pipeline_id !== currentId()) return
    draftRef.current = normalized
    setDraftState(normalized)
  }, [currentId])

  const refreshCheck = useCallback(
    async (revision: number) => {
      const targetId = pipelineId
      if (!targetId) return null
      const result = await checkPipeline(targetId, revision)
      if (!isCurrent(targetId, generation)) return null
      setCheck(result)
      return result
    },
    [generation, isCurrent, pipelineId],
  )

  const reload = useCallback(async () => {
    const targetId = pipelineId
    if (!targetId) {
      setDraft(null)
      setCheck(null)
      return null
    }
    const next = normalizeDraftResponse(await getPipelineDraft(targetId))
    if (!isCurrent(targetId, generation)) return null
    setDraft(next)
    const checkResult = await checkPipeline(targetId, next.revision)
    if (!isCurrent(targetId, generation)) return null
    setCheck(checkResult)
    setError(null)
    return { draft: next, check: checkResult }
  }, [generation, isCurrent, pipelineId, setDraft])

  useEffect(() => {
    draftRef.current = null
    setDraftState(null)
    setCheck(null)
    setError(null)
    setIsLoading(Boolean(pipelineId))
  }, [pipelineId])

  useEffect(() => {
    let active = true
    const effectGeneration = generation
    setIsLoading(Boolean(pipelineId))
    void (async () => {
      try {
        const nextCatalog = await getNodeCatalog()
        if (!active || !isCurrent(pipelineId, effectGeneration)) return
        setCatalog(nextCatalog)
        await reload()
      } catch (reason) {
        if (active && isCurrent(pipelineId, effectGeneration)) setError(reportError(reason))
      } finally {
        if (active && isCurrent(pipelineId, effectGeneration)) setIsLoading(false)
      }
    })()
    return () => {
      active = false
    }
  }, [generation, isCurrent, pipelineId, reload])

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
