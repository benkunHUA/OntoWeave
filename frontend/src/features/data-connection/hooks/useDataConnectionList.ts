import { useCallback, useEffect, useState } from 'react'
import { createDataConnection, listDataConnections } from '../../../shared/api'
import type { DataConnection } from '../../../shared/types'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'

function nextDraftName(items: DataConnection[]): string {
  const names = new Set(items.map((item) => item.name))
  if (!names.has('新数据连接')) return '新数据连接'
  for (let index = 2; index < 10_000; index += 1) {
    const candidate = `新数据连接 ${index}`
    if (!names.has(candidate)) return candidate
  }
  return `新数据连接 ${Date.now()}`
}

export function useDataConnectionList(report: ReportError) {
  const [items, setItems] = useState<DataConnection[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const page = await listDataConnections()
      setItems(page.items)
    } catch (reason) {
      report(reason, '加载数据连接失败')
    } finally {
      setIsLoading(false)
    }
  }, [report])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const createDraft = useCallback(async () => {
    const created = await createDataConnection({
      name: nextDraftName(items),
      kind: 'mysql',
    })
    setItems((current) => [created, ...current])
    return created
  }, [items])

  return { items, isLoading, refresh, createDraft }
}
