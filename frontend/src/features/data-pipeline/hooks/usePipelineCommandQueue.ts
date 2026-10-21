import { useCallback, useEffect, useRef, useState } from 'react'
import {
  applyPipelineDraftCommand,
  movePipelineNodes,
  undoPipelineTurn,
} from '../../../shared/api'
import { HttpError } from '../../../shared/api/client'
import type {
  JsonObject,
  PipelineDraftCommand,
  PipelineDraftResponse,
  PipelineEdge,
  PipelineNode,
  PipelineNodePositionUpdate,
} from '../../../shared/types'
import { normalizeDraftResponse } from '../lib/draft'

interface CommandQueueOptions {
  pipelineId: string | undefined
  draft: PipelineDraftResponse | null
  setDraft: (draft: PipelineDraftResponse) => void
  refreshCheck: (revision: number) => Promise<unknown>
  reload: () => Promise<unknown>
}

function isRevisionConflict(reason: unknown): boolean {
  return reason instanceof HttpError && reason.status === 409 &&
    reason.body.includes('REVISION_CONFLICT')
}

export function usePipelineCommandQueue({
  pipelineId,
  draft,
  setDraft,
  refreshCheck,
  reload,
}: CommandQueueOptions) {
  const [hasConflict, setHasConflict] = useState(false)
  const [isWriting, setIsWriting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const revisionRef = useRef(draft?.revision ?? 0)
  const haltedRef = useRef(false)
  const tailRef = useRef<Promise<unknown>>(Promise.resolve())
  const reloadRef = useRef(reload)

  useEffect(() => {
    revisionRef.current = draft?.revision ?? revisionRef.current
  }, [draft?.revision])

  useEffect(() => {
    reloadRef.current = reload
  }, [reload])

  const enqueue = useCallback(
    <T,>(
      operation: (baseRevision: number) => Promise<T>,
    ): Promise<T | null> => {
      const task = tailRef.current.then(async () => {
        if (haltedRef.current) return null
        setIsWriting(true)
        try {
          return await operation(revisionRef.current)
        } catch (reason) {
          if (isRevisionConflict(reason)) {
            haltedRef.current = true
            setHasConflict(true)
            await reloadRef.current()
            return null
          }
          setError(reason instanceof Error ? reason.message : '保存失败')
          return null
        } finally {
          setIsWriting(false)
        }
      })
      tailRef.current = task.catch(() => undefined)
      return task
    },
    [],
  )

  const sendCommand = useCallback(
    (payload: Omit<PipelineDraftCommand, 'base_revision' | 'operation_id'>) =>
      enqueue(async (baseRevision) => {
        if (!pipelineId) return null
        const operationId = crypto.randomUUID()
        const response = normalizeDraftResponse(
          await applyPipelineDraftCommand(pipelineId, {
            ...payload,
            base_revision: baseRevision,
            operation_id: operationId,
            turn_id: crypto.randomUUID(),
          }),
        )
        revisionRef.current = response.revision
        setDraft(response)
        await refreshCheck(response.revision)
        setError(null)
        return response
      }),
    [enqueue, pipelineId, refreshCheck, setDraft],
  )

  const addNode = useCallback(
    (node: PipelineNode) =>
      sendCommand({ type: 'add_node', node: { ...node } }),
    [sendCommand],
  )

  const updateNode = useCallback(
    (nodeId: string, name: string, config: JsonObject) =>
      sendCommand({ type: 'update_node', node: { id: nodeId, name, config } }),
    [sendCommand],
  )

  const deleteNode = useCallback(
    (nodeId: string) => sendCommand({ type: 'delete_node', node: { id: nodeId } }),
    [sendCommand],
  )

  const connectNodes = useCallback(
    (edge: PipelineEdge) => sendCommand({ type: 'connect', edge: { ...edge } }),
    [sendCommand],
  )

  const disconnectNodes = useCallback(
    (edgeId: string) => sendCommand({ type: 'disconnect', edge: { id: edgeId } }),
    [sendCommand],
  )

  const setOutput = useCallback(
    (output: JsonObject) => sendCommand({ type: 'set_output', output }),
    [sendCommand],
  )

  const moveNodes = useCallback(
    (positions: PipelineNodePositionUpdate[]) =>
      enqueue(async () => {
        if (!pipelineId || !positions.length) return null
        const response = normalizeDraftResponse(
          await movePipelineNodes(pipelineId, positions, crypto.randomUUID()),
        )
        revisionRef.current = response.revision
        setDraft(response)
        return response
      }),
    [enqueue, pipelineId, setDraft],
  )

  const undoTurn = useCallback(
    (turnId: string, revision: number) =>
      enqueue(async () => {
        if (!pipelineId) return null
        const response = normalizeDraftResponse(
          await undoPipelineTurn(
            pipelineId,
            turnId,
            revision,
            crypto.randomUUID(),
          ),
        )
        revisionRef.current = response.revision
        setDraft(response)
        await refreshCheck(response.revision)
        return response
      }),
    [enqueue, pipelineId, refreshCheck, setDraft],
  )

  const resumeAfterConflict = useCallback(async () => {
    await reloadRef.current()
    haltedRef.current = false
    setHasConflict(false)
    setError(null)
  }, [])

  return {
    addNode,
    updateNode,
    deleteNode,
    connectNodes,
    disconnectNodes,
    setOutput,
    moveNodes,
    undoTurn,
    resumeAfterConflict,
    hasConflict,
    isWriting,
    error,
    clearError: () => setError(null),
  }
}
