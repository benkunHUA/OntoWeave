import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import type { Dispatch, SetStateAction } from 'react'
import type { JsonObject, PipelineDraftResponse } from '../../../shared/types'
import { CONFIG_SAVE_DELAY_MS } from '../lib/constants'
import type { NodeConfigDrawerState } from '../lib/nodeConfigState'

export type NodeConfigSaveHandler = (
  pipelineId: string,
  nodeId: string,
  name: string,
  config: JsonObject,
) => Promise<PipelineDraftResponse | null> | null

interface UseNodeConfigSaveOptions {
  pipelineId: string
  nodeId: string
  revision: number | null
  isReadOnly: boolean
  state: NodeConfigDrawerState
  setState: Dispatch<SetStateAction<NodeConfigDrawerState>>
  onSave: NodeConfigSaveHandler
  delayMs?: number
}

interface SaveSnapshot {
  key: string
  pipelineId: string
  nodeId: string
  state: NodeConfigDrawerState
  isReadOnly: boolean
  onSave: NodeConfigSaveHandler
}

function keyFor(pipelineId: string, nodeId: string) {
  return `${pipelineId}:${nodeId}`
}

export function useNodeConfigSave({
  pipelineId,
  nodeId,
  revision,
  isReadOnly,
  state,
  setState,
  onSave,
  delayMs = CONFIG_SAVE_DELAY_MS,
}: UseNodeConfigSaveOptions) {
  const key = keyFor(pipelineId, nodeId)
  const currentRef = useRef<SaveSnapshot | null>(null)
  const snapshotsRef = useRef(new Map<string, SaveSnapshot>())
  const dirtyRef = useRef(new Map<string, boolean>())
  const revisionRef = useRef(new Map<string, number | null>())
  const drainsRef = useRef(new Map<string, Promise<number | null>>())

  useLayoutEffect(() => {
    const previous = currentRef.current
    if (previous && previous.key !== key && dirtyRef.current.get(previous.key)) {
      snapshotsRef.current.set(previous.key, previous)
    }
    currentRef.current = { key, pipelineId, nodeId, state, isReadOnly, onSave }
    dirtyRef.current.set(key, state.dirty)
    revisionRef.current.set(key, revision)
  }, [isReadOnly, key, nodeId, onSave, pipelineId, revision, state])

  const markDirty = useCallback((targetKey: string, dirty: boolean) => {
    dirtyRef.current.set(targetKey, dirty)
    const current = currentRef.current
    if (!current || current.key !== targetKey) return
    setState((value) => value.nodeId === current.nodeId
      ? { ...value, dirty }
      : value)
  }, [setState])

  const flushEntry = useCallback((entry: SaveSnapshot): Promise<number | null> => {
    const existing = drainsRef.current.get(entry.key)
    if (existing) return existing
    const drain = (async () => {
      while (true) {
        const current = currentRef.current?.key === entry.key
          ? currentRef.current
          : snapshotsRef.current.get(entry.key) ?? entry
        if (current.isReadOnly || !dirtyRef.current.get(entry.key)) {
          if (currentRef.current?.key !== entry.key) snapshotsRef.current.delete(entry.key)
          return revisionRef.current.get(entry.key) ?? null
        }
        const name = current.state.name.trim()
        if (!name) return null
        markDirty(entry.key, false)
        let response: PipelineDraftResponse | null
        try {
          const result = current.onSave(
            current.pipelineId,
            current.nodeId,
            name,
            { ...current.state.config },
          )
          response = await Promise.resolve(result)
        } catch {
          markDirty(entry.key, true)
          return null
        }
        if (!response) {
          markDirty(entry.key, true)
          return null
        }
        revisionRef.current.set(entry.key, response.revision)
        if (!dirtyRef.current.get(entry.key)) {
          if (currentRef.current?.key !== entry.key) snapshotsRef.current.delete(entry.key)
          return response.revision
        }
      }
    })()
    const tracked = drain.finally(() => {
      drainsRef.current.delete(entry.key)
    })
    drainsRef.current.set(entry.key, tracked)
    return tracked
  }, [markDirty])

  const flush = useCallback(() => {
    const current = currentRef.current
    return current ? flushEntry(current) : Promise.resolve<number | null>(null)
  }, [flushEntry])

  useEffect(() => {
    if (!state.dirty || isReadOnly || !state.name.trim()) return
    const timer = window.setTimeout(() => void flush(), delayMs)
    return () => window.clearTimeout(timer)
  }, [delayMs, flush, isReadOnly, state.config, state.dirty, state.name])

  useEffect(() => {
    const entry = currentRef.current
    return () => {
      if (entry) void flushEntry(entry)
    }
  }, [flushEntry, key])

  const setRevision = useCallback((next: number) => {
    revisionRef.current.set(key, next)
  }, [key])

  return { flush, setRevision }
}
