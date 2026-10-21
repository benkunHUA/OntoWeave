import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getPipelineSchedule,
  listPipelineScheduleRuns,
  pausePipelineSchedule,
  putPipelineSchedule,
  resumePipelineSchedule,
} from '../../../shared/api'
import { HttpError } from '../../../shared/api/client'
import { httpErrorText } from '../../../shared/api/errors'
import type {
  PipelineSchedule,
  PipelineSchedulePutRequest,
  PipelineScheduleRun,
} from '../../../shared/types'
import { usePipelineGeneration } from './usePipelineGeneration'

export function usePipelineSchedule(pipelineId: string | undefined) {
  const [schedule, setSchedule] = useState<PipelineSchedule | null>(null)
  const [runs, setRuns] = useState<PipelineScheduleRun[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [missing, setMissing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestRef = useRef(0)
  const { token, isCurrent } = usePipelineGeneration(pipelineId)

  const refresh = useCallback(async () => {
    if (!pipelineId) return null
    const requestToken = ++requestRef.current
    setIsLoading(true)
    setError(null)
    try {
      const current = await getPipelineSchedule(pipelineId)
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      const history = await listPipelineScheduleRuns(pipelineId, 50, 0)
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      setSchedule(current)
      setRuns(history)
      setMissing(false)
      return current
    } catch (reason) {
      if (!isCurrent(pipelineId, token) || requestRef.current !== requestToken) return null
      if (reason instanceof HttpError && reason.status === 404) {
        setSchedule(null)
        setRuns([])
        setMissing(true)
      } else {
        setError(httpErrorText(reason, '读取调度失败'))
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
    setSchedule(null)
    setRuns([])
    setMissing(false)
    setError(null)
    void refresh()
  }, [refresh])

  const put = useCallback(async (payload: PipelineSchedulePutRequest) => {
    if (!pipelineId) return null
    try {
      const next = await putPipelineSchedule(pipelineId, payload, crypto.randomUUID())
      if (!isCurrent(pipelineId, token)) return null
      setSchedule(next)
      setMissing(false)
      await refresh()
      return next
    } catch (reason) {
      if (isCurrent(pipelineId, token)) setError(httpErrorText(reason, '保存调度失败'))
      throw reason
    }
  }, [isCurrent, pipelineId, refresh, token])

  const pause = useCallback(async () => {
    if (!pipelineId) return null
    try {
      const next = await pausePipelineSchedule(pipelineId, crypto.randomUUID())
      if (!isCurrent(pipelineId, token)) return null
      setSchedule(next)
      await refresh()
      return next
    } catch (reason) {
      if (isCurrent(pipelineId, token)) setError(httpErrorText(reason, '暂停调度失败'))
      throw reason
    }
  }, [isCurrent, pipelineId, refresh, token])

  const resume = useCallback(async () => {
    if (!pipelineId) return null
    try {
      const next = await resumePipelineSchedule(pipelineId, crypto.randomUUID())
      if (!isCurrent(pipelineId, token)) return null
      setSchedule(next)
      await refresh()
      return next
    } catch (reason) {
      if (isCurrent(pipelineId, token)) setError(httpErrorText(reason, '恢复调度失败'))
      throw reason
    }
  }, [isCurrent, pipelineId, refresh, token])

  return { schedule, runs, isLoading, missing, error, refresh, put, pause, resume }
}
