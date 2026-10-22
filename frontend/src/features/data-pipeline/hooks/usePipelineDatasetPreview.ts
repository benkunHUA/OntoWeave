import { useEffect, useState } from 'react'
import { getPipelineDatasetPreview } from '../../../shared/api'
import { httpErrorText } from '../../../shared/api/errors'
import type { DatasetPreview } from '../../../shared/types'

interface UsePipelineDatasetPreviewOptions {
  pipelineId: string
  datasetId: string | null
  versionId: string | null
  limit: number
  enabled?: boolean
}

export function usePipelineDatasetPreview({
  pipelineId,
  datasetId,
  versionId,
  limit,
  enabled = true,
}: UsePipelineDatasetPreviewOptions) {
  const [preview, setPreview] = useState<DatasetPreview | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    if (!enabled || !datasetId || !versionId) {
      setPreview(null)
      setError(null)
      setIsLoading(false)
      return () => {
        active = false
      }
    }
    setPreview(null)
    setError(null)
    setIsLoading(true)
    void (async () => {
      try {
        const value = await getPipelineDatasetPreview(
          pipelineId,
          datasetId,
          versionId,
          limit,
        )
        if (active) setPreview(value)
      } catch (reason) {
        if (active) setError(httpErrorText(reason, '读取发布数据失败'))
      } finally {
        if (active) setIsLoading(false)
      }
    })()
    return () => {
      active = false
    }
  }, [datasetId, enabled, limit, pipelineId, versionId])

  return { preview, isLoading, error }
}
