import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cancelPipelineBuild,
  createPipelineBuild,
  getPipelineBuild,
} from '../../../shared/api'
import { HttpError } from '../../../shared/api/client'
import type { PipelineBuild } from '../../../shared/types'
import { BUILD_POLL_INTERVAL_MS, BUILD_STORAGE_PREFIX } from '../lib/constants'
import { BUILD_ACTIVE_STATUSES } from '../lib/executionConstants'
import { usePipelineGeneration } from './usePipelineGeneration'

function storageKey(pipelineId: string) {
  return `${BUILD_STORAGE_PREFIX}${pipelineId}`
}

function readStoredBuild(pipelineId: string | undefined): string | null {
  if (!pipelineId || typeof window === 'undefined') return null
  return window.localStorage.getItem(storageKey(pipelineId))
}

function storeBuild(pipelineId: string, buildId: string | null) {
  if (typeof window === 'undefined') return
  if (buildId) window.localStorage.setItem(storageKey(pipelineId), buildId)
  else window.localStorage.removeItem(storageKey(pipelineId))
}

export function usePipelineBuild(pipelineId: string | undefined) {
  const [build, setBuild] = useState<PipelineBuild | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const timerRef = useRef<number | null>(null)
  const requestRef = useRef(0)
  const { token, isCurrent } = usePipelineGeneration(pipelineId)

  const stopPolling = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const load = useCallback(async (buildId: string, open = false) => {
    if (!pipelineId) return null
    const requestToken = ++requestRef.current
    setIsLoading(true)
    setError(null)
    try {
      const next = await getPipelineBuild(pipelineId, buildId)
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setBuild(next)
      storeBuild(pipelineId, next.id)
      if (open) setDetailsOpen(true)
      return next
    } catch (reason) {
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      if (reason instanceof HttpError && reason.status === 404) {
        storeBuild(pipelineId, null)
        setBuild(null)
        setDetailsOpen(false)
      } else {
        setError(reason instanceof Error ? reason.message : '读取构建失败')
      }
      return null
    } finally {
      if (isCurrent(pipelineId, token) && requestRef.current === requestToken) {
        setIsLoading(false)
      }
    }
  }, [isCurrent, pipelineId, token])

  useEffect(() => {
    requestRef.current += 1
    stopPolling()
    setBuild(null)
    setError(null)
    setDetailsOpen(false)
    const stored = readStoredBuild(pipelineId)
    if (stored && pipelineId) void load(stored)
    return stopPolling
  }, [load, pipelineId, stopPolling, token])

  useEffect(() => {
    if (!pipelineId || !build || !BUILD_ACTIVE_STATUSES.has(build.status)) return
    let cancelled = false
    const pollRequest = requestRef.current
    const tick = async () => {
      try {
        const next = await getPipelineBuild(pipelineId, build.id)
        if (cancelled || !isCurrent(pipelineId, token) || requestRef.current !== pollRequest) return
        setBuild(next)
        storeBuild(pipelineId, next.id)
        if (BUILD_ACTIVE_STATUSES.has(next.status)) {
          timerRef.current = window.setTimeout(tick, BUILD_POLL_INTERVAL_MS)
        }
      } catch {
        if (!cancelled && isCurrent(pipelineId, token) && requestRef.current === pollRequest) {
          timerRef.current = window.setTimeout(tick, BUILD_POLL_INTERVAL_MS)
        }
      }
    }
    timerRef.current = window.setTimeout(tick, BUILD_POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      stopPolling()
    }
  }, [build, isCurrent, pipelineId, stopPolling, token])

  const create = useCallback(async (draftRevision: number) => {
    if (!pipelineId) return null
    const requestToken = ++requestRef.current
    const operationId = crypto.randomUUID()
    setIsLoading(true)
    setError(null)
    try {
      const next = await createPipelineBuild(pipelineId, {
        draft_revision: draftRevision,
        operation_id: operationId,
      })
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setBuild(next)
      storeBuild(pipelineId, next.id)
      setDetailsOpen(true)
      return next
    } catch (reason) {
      if (isCurrent(pipelineId, token) && requestRef.current === requestToken) {
        setError(reason instanceof Error ? reason.message : '创建构建失败')
      }
      return null
    } finally {
      if (isCurrent(pipelineId, token) && requestRef.current === requestToken) {
        setIsLoading(false)
      }
    }
  }, [isCurrent, pipelineId, token])

  const open = useCallback((buildId: string) => {
    storeBuild(pipelineId ?? '', buildId)
    void load(buildId, true)
  }, [load, pipelineId])

  const cancel = useCallback(async () => {
    if (!pipelineId || !build) return
    try {
      await cancelPipelineBuild(pipelineId, build.id, crypto.randomUUID())
      if (isCurrent(pipelineId, token)) await load(build.id)
    } catch (reason) {
      if (isCurrent(pipelineId, token)) {
        setError(reason instanceof Error ? reason.message : '取消构建失败')
      }
    }
  }, [build, isCurrent, load, pipelineId, token])

  return {
    build,
    isLoading,
    error,
    detailsOpen,
    setDetailsOpen,
    create,
    open,
    cancel,
    reload: () => build ? load(build.id) : Promise.resolve(null),
  }
}
