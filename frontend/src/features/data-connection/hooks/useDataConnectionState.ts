import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getDataConnectionState,
  listTables,
} from '../../../shared/api'
import type { DataConnectionState, TableSummary } from '../../../shared/types'

interface RefreshOptions {
  withTables?: boolean
}

export function useDataConnectionState(connectionId: string | undefined) {
  const [state, setState] = useState<DataConnectionState | null>(null)
  const [tables, setTables] = useState<TableSummary[]>([])
  const [warnings, setWarnings] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestRef = useRef(0)

  const loadTables = useCallback(async (targetId: string, requestId: number) => {
    const result = await listTables(targetId)
    if (requestRef.current !== requestId) return
    setTables(result.tables)
    setWarnings(result.warnings)
  }, [])

  const refresh = useCallback(
    async (options: RefreshOptions = {}) => {
      if (!connectionId) {
        setState(null)
        setTables([])
        setWarnings([])
        return
      }
      const requestId = requestRef.current + 1
      requestRef.current = requestId
      setIsRefreshing(true)
      try {
        const next = await getDataConnectionState(connectionId)
        if (requestRef.current !== requestId) return
        setState(next)
        setError(null)
        if (options.withTables && next.connection.status === 'active') {
          await loadTables(connectionId, requestId)
        }
      } catch (reason) {
        if (requestRef.current !== requestId) return
        setError(reason instanceof Error ? reason.message : '加载连接状态失败')
      } finally {
        if (requestRef.current === requestId) setIsRefreshing(false)
      }
    },
    [connectionId, loadTables],
  )

  const refreshState = useCallback(async () => {
    await refresh()
  }, [refresh])

  useEffect(() => {
    setIsLoading(true)
    setState(null)
    setTables([])
    setWarnings([])
    setError(null)
    void refresh({ withTables: true }).finally(() => setIsLoading(false))
  }, [refresh])

  return {
    state,
    tables,
    warnings,
    isLoading,
    isRefreshing,
    error,
    refresh,
    refreshState,
    loadTables: () => (connectionId ? refresh({ withTables: true }) : Promise.resolve()),
  }
}
