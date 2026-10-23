import { useCallback, useEffect, useRef, useState } from 'react'
import { getOntologyResources } from '../../../shared/api/ontology'
import { httpErrorText } from '../../../shared/api/errors'
import type { OntologyResources } from '../../../shared/types'

export function useOntologyResources() {
  const [resources, setResources] = useState<OntologyResources | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const requestRef = useRef(0)

  const refresh = useCallback(async () => {
    const requestId = ++requestRef.current
    setIsLoading(true)
    setError(null)
    try {
      const next = await getOntologyResources()
      if (requestRef.current === requestId) setResources(next)
    } catch (reason) {
      if (requestRef.current === requestId) setError(httpErrorText(reason, '加载对象与关系失败'))
    } finally {
      if (requestRef.current === requestId) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
    return () => {
      requestRef.current += 1
    }
  }, [refresh])

  return { resources, isLoading, error, refresh }
}
