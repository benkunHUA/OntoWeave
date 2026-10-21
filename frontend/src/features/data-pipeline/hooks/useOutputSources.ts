import { useCallback, useEffect, useRef, useState } from 'react'
import { getPipelineOutputSources } from '../../../shared/api'
import { httpErrorText } from '../../../shared/api/errors'
import type { PipelineSource } from '../../../shared/types'
import { usePipelineGeneration } from './usePipelineGeneration'

interface UseOutputSourcesOptions {
  pipelineId: string | undefined
  datasetId: string | null | undefined
  datasetVersionId: string | null | undefined
  enabled?: boolean
}

export function useOutputSources({
  pipelineId,
  datasetId,
  datasetVersionId,
  enabled = true,
}: UseOutputSourcesOptions) {
  const [source, setSource] = useState<PipelineSource | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestRef = useRef(0)
  const { token, isCurrent } = usePipelineGeneration(pipelineId)

  const load = useCallback(async () => {
    const requestToken = ++requestRef.current
    if (!enabled || !pipelineId || !datasetId) {
      setSource(null)
      setError(null)
      setIsLoading(false)
      return null
    }
    setIsLoading(true)
    setError(null)
    try {
      const next = await getPipelineOutputSources(
        pipelineId,
        datasetId,
        datasetVersionId ?? undefined,
      )
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setSource(next)
      return next
    } catch (reason) {
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setError(httpErrorText(reason, '读取输出来源失败'))
      return null
    } finally {
      if (isCurrent(pipelineId, token) && requestRef.current === requestToken) {
        setIsLoading(false)
      }
    }
  }, [datasetVersionId, datasetId, enabled, isCurrent, pipelineId, token])

  useEffect(() => {
    requestRef.current += 1
    setSource(null)
    setError(null)
    void load()
  }, [load])

  return { source, isLoading, error, reload: load }
}
