import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { previewPipelineNode } from '../../../shared/api'
import type { PipelinePreview } from '../../../shared/types'
import {
  previewRequestError,
  previewState,
  type PreviewRequestError,
} from '../lib/preview'
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
  const { token, isCurrent } = usePipelineGeneration(pipelineId)
  const state = useMemo(() => previewState({
    previewRevision: preview?.draft_revision ?? null,
    draftRevision,
    isLoading,
    hasError: Boolean(error),
  }), [draftRevision, error, isLoading, preview?.draft_revision])

  const run = useCallback(async (targetNodeId: string) => {
    if (!pipelineId || draftRevision === null) return null
    const requestToken = ++requestRef.current
    const requestId = crypto.randomUUID()
    setNodeId(targetNodeId)
    setIsLoading(true)
    setError(null)
    try {
      const result = await previewPipelineNode(pipelineId, {
        draft_revision: draftRevision,
        node_id: targetNodeId,
        request_id: requestId,
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
        setIsLoading(false)
      }
    }
  }, [draftRevision, isCurrent, pipelineId, token])

  const close = useCallback(() => {
    requestRef.current += 1
    setNodeId(null)
    setPreview(null)
    setError(null)
    setIsLoading(false)
  }, [])

  useEffect(() => {
    requestRef.current += 1
    setPreview(null)
    setNodeId(null)
    setError(null)
    setIsLoading(false)
  }, [pipelineId])

  return {
    preview,
    nodeId,
    state,
    isLoading,
    error,
    run,
    close,
  }
}
