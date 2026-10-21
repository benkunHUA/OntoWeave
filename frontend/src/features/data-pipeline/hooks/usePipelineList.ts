import { useCallback, useEffect, useState } from 'react'
import {
  archivePipeline,
  createPipeline,
  listPipelines,
  renamePipeline,
} from '../../../shared/api'
import type { Pipeline } from '../../../shared/types'

function reportError(reason: unknown): string {
  return reason instanceof Error ? reason.message : '操作失败'
}

export function usePipelineList() {
  const [items, setItems] = useState<Pipeline[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const page = await listPipelines(200, 0)
      setItems(page.items)
      setError(null)
    } catch (reason) {
      setError(reportError(reason))
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const create = useCallback(
    async (name: string) => {
      try {
        const created = await createPipeline({ name }, crypto.randomUUID())
        setItems((current) => [created, ...current.filter((item) => item.id !== created.id)])
        await refresh()
        return created
      } catch (reason) {
        setError(reportError(reason))
        return null
      }
    },
    [refresh],
  )

  const rename = useCallback(
    async (pipelineId: string, name: string) => {
      try {
        const updated = await renamePipeline(
          pipelineId,
          { name },
          crypto.randomUUID(),
        )
        setItems((current) =>
          current.map((pipeline) => (pipeline.id === pipelineId ? updated : pipeline)),
        )
        return updated
      } catch (reason) {
        setError(reportError(reason))
        return null
      }
    },
    [],
  )

  const archive = useCallback(
    async (pipelineId: string) => {
      try {
        await archivePipeline(pipelineId, crypto.randomUUID())
        setItems((current) => current.filter((pipeline) => pipeline.id !== pipelineId))
        return true
      } catch (reason) {
        setError(reportError(reason))
        return false
      }
    },
    [],
  )

  return { items, isLoading, error, refresh, create, rename, archive }
}
