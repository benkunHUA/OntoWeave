import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cancelIndexRun,
  getIndexRun,
  listObjectBindingRuns,
  listRelationBindingRuns,
} from '../../../shared/api/ontology'
import { httpErrorText } from '../../../shared/api/errors'
import type { IndexRun, OntologyResource } from '../../../shared/types'
import { RUN_POLL_INTERVAL_MS, isActiveRun } from '../lib/constants'
import { formatResourceRef } from '../lib/format'

function listRuns(kind: OntologyResource['kind'], id: string): Promise<IndexRun[]> {
  return kind === 'object_type' ? listObjectBindingRuns(id) : listRelationBindingRuns(id)
}

export function useIndexRun(resource: OntologyResource | null) {
  const [run, setRun] = useState<IndexRun | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const resourceKind = resource?.kind
  const resourceId = resource?.id
  const resourceKey = resource ? formatResourceRef(resource) : ''
  const currentResourceRef = useRef(resourceKey)

  const findCurrentRun = useCallback(async () => {
    if (!resourceKind || !resourceId || currentResourceRef.current !== resourceKey) return
    setIsLoading(true)
    setError(null)
    try {
      const runs = await listRuns(resourceKind, resourceId)
      if (currentResourceRef.current !== resourceKey) return
      setRun(runs.find(isActiveRun) ?? runs[0] ?? null)
    } catch (reason) {
      if (currentResourceRef.current === resourceKey) {
        setError(httpErrorText(reason, '读取运行记录失败'))
      }
    } finally {
      if (currentResourceRef.current === resourceKey) setIsLoading(false)
    }
  }, [resourceId, resourceKey, resourceKind])

  useEffect(() => {
    currentResourceRef.current = resourceKey
    setRun(null)
    setError(null)
    if (!resourceKind || !resourceId) {
      setIsLoading(false)
      return
    }
    void findCurrentRun()
    return () => {
      if (currentResourceRef.current === resourceKey) currentResourceRef.current = ''
    }
  }, [findCurrentRun, resourceId, resourceKey, resourceKind])

  const active = isActiveRun(run)
  const activeRunId = active ? run.id : null
  useEffect(() => {
    if (!activeRunId) return
    let cancelled = false
    const timer = window.setInterval(() => {
      void getIndexRun(activeRunId)
        .then((next) => {
          if (!cancelled && currentResourceRef.current === resourceKey) setRun(next)
        })
        .catch((reason: unknown) => {
          if (!cancelled && currentResourceRef.current === resourceKey) {
            setError(httpErrorText(reason, '刷新运行状态失败'))
          }
        })
    }, RUN_POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [activeRunId, resourceKey])

  const cancel = useCallback(async () => {
    if (!run || !isActiveRun(run)) return null
    try {
      const next = await cancelIndexRun(run.id, crypto.randomUUID())
      if (currentResourceRef.current === resourceKey) setRun(next)
      return next
    } catch (reason) {
      setError(httpErrorText(reason, '取消运行失败'))
      return null
    }
  }, [resourceKey, run])

  return { run, isLoading, isPolling: active, error, refresh: findCurrentRun, cancel }
}
