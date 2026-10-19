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
  const stateRequestRef = useRef(0)
  const tablesRequestRef = useRef(0)

  const reloadTables = useCallback(async () => {
    if (!connectionId) return
    const requestId = tablesRequestRef.current + 1
    tablesRequestRef.current = requestId
    try {
      const result = await listTables(connectionId)
      if (tablesRequestRef.current !== requestId) return
      setTables(result.tables)
      setWarnings(result.warnings)
    } catch (reason) {
      if (tablesRequestRef.current !== requestId) return
      setError(reason instanceof Error ? reason.message : '加载数据表失败')
    }
  }, [connectionId])

  const refresh = useCallback(
    async (options: RefreshOptions = {}) => {
      if (!connectionId) {
        setState(null)
        setTables([])
        setWarnings([])
        return
      }
      const requestId = stateRequestRef.current + 1
      stateRequestRef.current = requestId
      setIsRefreshing(true)
      try {
        const next = await getDataConnectionState(connectionId)
        if (stateRequestRef.current !== requestId) return
        setState(next)
        setError(null)
        if (options.withTables && next.connection.status === 'active') {
          await reloadTables()
        }
      } catch (reason) {
        if (stateRequestRef.current !== requestId) return
        setError(reason instanceof Error ? reason.message : '加载连接状态失败')
      } finally {
        if (stateRequestRef.current === requestId) setIsRefreshing(false)
      }
    },
    [connectionId, reloadTables],
  )

  const refreshState = useCallback(async () => {
    await refresh()
  }, [refresh])

  useEffect(() => {
    tablesRequestRef.current += 1
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
    reloadTables,
    refreshAll: () => (connectionId ? refresh({ withTables: true }) : Promise.resolve()),
  }
}
