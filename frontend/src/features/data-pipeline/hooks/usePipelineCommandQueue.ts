import { useCallback, useEffect, useRef, useState } from 'react'
import {
  applyPipelineDraftCommand,
  movePipelineNodes,
  undoPipelineTurn,
} from '../../../shared/api'
import { HttpError } from '../../../shared/api/client'
import { httpErrorText } from '../../../shared/api/errors'
import type {
  JsonObject,
  PipelineDraftCommand,
  PipelineDraftResponse,
  PipelineEdge,
  PipelineNode,
  PipelineNodePositionUpdate,
} from '../../../shared/types'
import { buildDraftCommand } from '../lib/command'
import { normalizeDraftResponse } from '../lib/draft'
import { setOutputPayload, type OutputDraftInput } from '../lib/outputCommand'
import { usePipelineGeneration } from './usePipelineGeneration'

interface CommandQueueOptions {
  pipelineId: string | undefined
  draft: PipelineDraftResponse | null
  setDraft: (draft: PipelineDraftResponse) => void
  refreshCheck: (revision: number) => Promise<unknown>
  reload: () => Promise<unknown>
}

type IsActive = () => boolean

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
  const revisionsRef = useRef(new Map<string, number>())
  const haltedRef = useRef(false)
  const tailRef = useRef<Promise<unknown>>(Promise.resolve())
  const reloadRef = useRef(reload)
  const { token: generation, isCurrent } = usePipelineGeneration(pipelineId)

  useEffect(() => {
    haltedRef.current = false
    tailRef.current = Promise.resolve()
    setHasConflict(false)
    setIsWriting(false)
    setError(null)
  }, [pipelineId])

  useEffect(() => {
    if (draft) revisionsRef.current.set(draft.pipeline_id, draft.revision)
  }, [draft])

  useEffect(() => {
    reloadRef.current = reload
  }, [reload])

  const enqueue = useCallback(
    <T,>(
      operation: (baseRevision: number, isActive: IsActive) => Promise<T>,
    ): Promise<T | null> => {
      const runGeneration = generation
      const runPipeline = pipelineId
      const isActive: IsActive = () => isCurrent(runPipeline, runGeneration)
      const task = tailRef.current.then(async () => {
        if (haltedRef.current) return null
        const baseRevision = runPipeline
          ? revisionsRef.current.get(runPipeline) ?? 0
          : 0
        setIsWriting(true)
        try {
          const result = await operation(baseRevision, isActive)
          return isActive() ? result : null
        } catch (reason) {
          if (!isActive()) return null
          if (isRevisionConflict(reason)) {
            haltedRef.current = true
            setHasConflict(true)
            await reloadRef.current()
            return null
          }
          setError(httpErrorText(reason, '保存失败'))
          return null
        } finally {
          if (isActive()) setIsWriting(false)
        }
      })
      tailRef.current = task.catch(() => undefined)
      return task
    },
    [generation, isCurrent, pipelineId],
  )

  const sendCommand = useCallback(
    (payload: Omit<PipelineDraftCommand, 'base_revision' | 'operation_id'>) =>
      enqueue(async (baseRevision, isActive) => {
        if (!pipelineId) return null
        const operationId = crypto.randomUUID()
        const response = normalizeDraftResponse(
          await applyPipelineDraftCommand(
            pipelineId,
            buildDraftCommand(payload, baseRevision, operationId),
          ),
        )
        if (!isActive()) return null
        revisionsRef.current.set(response.pipeline_id, response.revision)
        setDraft(response)
        await refreshCheck(response.revision)
        if (!isActive()) return null
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
    (nodeId: string, output: OutputDraftInput) =>
      sendCommand(setOutputPayload(nodeId, output)),
    [sendCommand],
  )

  const moveNodes = useCallback(
    (positions: PipelineNodePositionUpdate[]) =>
      enqueue(async (_, isActive) => {
        if (!pipelineId || !positions.length) return null
        const response = normalizeDraftResponse(
          await movePipelineNodes(pipelineId, positions, crypto.randomUUID()),
        )
        if (!isActive()) return null
        revisionsRef.current.set(response.pipeline_id, response.revision)
        setDraft(response)
        return response
      }),
    [enqueue, pipelineId, setDraft],
  )

  const undoTurn = useCallback(
    (turnId: string, revision: number) =>
      enqueue(async (_, isActive) => {
        if (!pipelineId) return null
        const response = normalizeDraftResponse(
          await undoPipelineTurn(
            pipelineId,
            turnId,
            revision,
            crypto.randomUUID(),
          ),
        )
        if (!isActive()) return null
        revisionsRef.current.set(response.pipeline_id, response.revision)
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
