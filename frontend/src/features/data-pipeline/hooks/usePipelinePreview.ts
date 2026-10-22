import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { cancelPipelinePreview, previewPipelineNode } from '../../../shared/api'
import type { PipelinePreview } from '../../../shared/types'
import {
  previewRequestError,
  previewState,
  type PreviewRequestError,
} from '../lib/preview'
import { PIPELINE_PREVIEW_ROW_LIMIT } from '../lib/executionConstants'
import { usePipelineGeneration } from './usePipelineGeneration'

interface UsePipelinePreviewOptions {
  pipelineId: string | undefined
  draftRevision: number | null
}

export function usePipelinePreview({
  pipelineId,
  draftRevision,
}: UsePipelinePreviewOptions) {
  const [preview, setPreview] = useState<PipelinePreview | null>(null)
  const [nodeId, setNodeId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<PreviewRequestError | null>(null)
  const requestRef = useRef(0)
  const activeRef = useRef<{ pipelineId: string; nodeId: string } | null>(null)
  const { token, isCurrent } = usePipelineGeneration(pipelineId)
  const state = useMemo(() => previewState({
    previewRevision: preview?.draft_revision ?? null,
    draftRevision,
    isLoading,
    hasError: Boolean(error),
  }), [draftRevision, error, isLoading, preview?.draft_revision])

  const cancelActive = useCallback(() => {
    const active = activeRef.current
    activeRef.current = null
    if (active) void cancelPipelinePreview(active.pipelineId).catch(() => undefined)
  }, [])

  const run = useCallback(async (targetNodeId: string, revisionOverride?: number) => {
    const revision = revisionOverride ?? draftRevision
    if (!pipelineId || revision === null) return null
    setPreview(null)
    if (activeRef.current) {
      activeRef.current = null
      await cancelPipelinePreview(pipelineId).catch(() => undefined)
    }
    const requestToken = ++requestRef.current
    const requestId = crypto.randomUUID()
    activeRef.current = { pipelineId, nodeId: targetNodeId }
    setNodeId(targetNodeId)
    setIsLoading(true)
    setError(null)
    try {
      const result = await previewPipelineNode(pipelineId, {
        draft_revision: revision,
        node_id: targetNodeId,
        request_id: requestId,
        row_limit: PIPELINE_PREVIEW_ROW_LIMIT,
      }, requestId)
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setPreview(result)
      return result
    } catch (reason) {
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setPreview(null)
      setError(previewRequestError(reason))
      return null
    } finally {
      if (isCurrent(pipelineId, token) && requestRef.current === requestToken) {
        activeRef.current = null
        setIsLoading(false)
      }
    }
  }, [draftRevision, isCurrent, pipelineId, token])

  const close = useCallback(() => {
    requestRef.current += 1
    cancelActive()
    setNodeId(null)
    setPreview(null)
    setError(null)
    setIsLoading(false)
  }, [cancelActive])

  useEffect(() => {
    requestRef.current += 1
    cancelActive()
    setPreview(null)
    setNodeId(null)
    setError(null)
    setIsLoading(false)
  }, [cancelActive, pipelineId])

  useEffect(() => () => {
    requestRef.current += 1
    cancelActive()
  }, [cancelActive])

  return {
    preview,
    nodeId,
    state,
    isLoading,
    error,
    run,
    close,
    cancel: cancelActive,
  }
}
