// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '../../../../test/render'
import { createRef, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  JsonObject,
  NodeDefinition,
  PipelineDraftResponse,
  PipelineNode,
} from '../../../../shared/types'
import { NodeConfigDrawer, type NodeConfigDrawerHandle } from './NodeConfigDrawer'

const nodeA: PipelineNode = {
  id: '00000000-0000-4000-8000-000000000001',
  type: 'filter',
  name: 'Filter A',
  config: { condition: { kind: 'compare', field: 'id', op: 'is_not_null' } },
  position: { x: 0, y: 0 },
}

const nodeB: PipelineNode = {
  id: '00000000-0000-4000-8000-000000000002',
  type: 'filter',
  name: 'Filter B',
  config: { condition: { kind: 'compare', field: 'status', op: 'eq' } },
  position: { x: 100, y: 0 },
}

const definition: NodeDefinition = {
  type: 'filter',
  version: 1,
  label: '数据筛选',
  category: 'transform',
  ports: [],
  config_schema: { type: 'object', properties: { condition: { type: 'object' } } },
  ui_schema: { fields: { condition: { widget: 'condition-tree' } } },
}

const textNode: PipelineNode = {
  id: '00000000-0000-4000-8000-000000000003',
  type: 'filter',
  name: 'Text filter',
  config: { pattern: 'initial' },
  position: { x: 200, y: 0 },
}

const textDefinition: NodeDefinition = {
  ...definition,
  config_schema: { type: 'object', properties: { pattern: { type: 'string' } } },
  ui_schema: { fields: { pattern: { widget: 'text' } } },
}

function draft(revision: number): PipelineDraftResponse {
  return {
    pipeline_id: 'pipeline-a',
    revision,
    definition: { schemaVersion: 1, nodes: [nodeA], edges: [] },
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function drawer(
  pipelineId: string,
  node: PipelineNode,
  onSave: (
    pipelineId: string,
    nodeId: string,
    name: string,
    config: JsonObject,
  ) => Promise<PipelineDraftResponse | null> | null,
) {
  return (
    <NodeConfigDrawer
      pipelineId={pipelineId}
      revision={1}
      node={node}
      definition={definition}
      issues={[]}
      isSaving={false}
      onClose={() => undefined}
      onSave={onSave}
      onDelete={() => null}
      onSetOutput={() => null}
    />
  )
}

function ClosingDrawer({ onSave }: {
  onSave: (
    pipelineId: string,
    nodeId: string,
    name: string,
    config: JsonObject,
  ) => Promise<PipelineDraftResponse | null> | null
}) {
  const [open, setOpen] = useState(true)
  if (!open) return null
  return (
    <NodeConfigDrawer
      pipelineId="pipeline-a"
      revision={1}
      node={nodeA}
      definition={definition}
      issues={[]}
      isSaving={false}
      onClose={() => setOpen(false)}
      onSave={onSave}
      onDelete={() => null}
      onSetOutput={() => null}
    />
  )
}

describe('NodeConfigDrawer pending save flush', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('flushes an edit before the debounce when unmounted', () => {
    const onSave = vi.fn()
    const view = render(drawer('pipeline-a', nodeA, onSave))

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'Filter edited' } })
    act(() => vi.advanceTimersByTime(100))
    act(() => view.unmount())

    expect(onSave).toHaveBeenCalledWith(
      'pipeline-a',
      nodeA.id,
      'Filter edited',
      nodeA.config,
    )
  })

  it('restarts the debounce for consecutive config edits and saves only the latest', async () => {
    const onSave = vi.fn().mockResolvedValue(draft(2))
    render(
      <NodeConfigDrawer
        pipelineId="pipeline-a"
        revision={1}
        node={textNode}
        definition={textDefinition}
        issues={[]}
        isSaving={false}
        onClose={() => undefined}
        onSave={onSave}
        onDelete={() => null}
        onSetOutput={() => null}
      />,
    )

    fireEvent.change(screen.getByDisplayValue('initial'), { target: { value: 'first' } })
    act(() => vi.advanceTimersByTime(200))
    fireEvent.change(screen.getByDisplayValue('first'), { target: { value: 'latest' } })
    act(() => vi.advanceTimersByTime(399))

    expect(onSave).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(1))
    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave).toHaveBeenCalledWith(
      'pipeline-a',
      textNode.id,
      'Text filter',
      { pattern: 'latest' },
    )
    await act(async () => {
      await Promise.resolve()
    })
  })

  it('flushes the old node to its own pipeline when switching nodes', () => {
    const first = vi.fn()
    const second = vi.fn()
    const view = render(drawer('pipeline-a', nodeA, first))

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'Filter edited' } })
    act(() => vi.advanceTimersByTime(100))
    view.rerender(drawer('pipeline-b', nodeB, second))

    expect(first).toHaveBeenCalledWith(
      'pipeline-a',
      nodeA.id,
      'Filter edited',
      nodeA.config,
    )
    expect(second).not.toHaveBeenCalled()
  })

  it('saves exactly once when closing closes the drawer', () => {
    const onSave = vi.fn()
    render(<ClosingDrawer onSave={onSave} />)

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'Filter edited' } })
    fireEvent.click(screen.getByLabelText('关闭'))

    expect(onSave).toHaveBeenCalledTimes(1)
  })

  it('waits for the pending save before closing', async () => {
    const pending = deferred<PipelineDraftResponse>()
    const onSave = vi.fn().mockReturnValue(pending.promise)
    const onClose = vi.fn()
    render(
      <NodeConfigDrawer
        pipelineId="pipeline-a"
        revision={1}
        node={nodeA}
        definition={definition}
        issues={[]}
        isSaving={false}
        onClose={onClose}
        onSave={onSave}
        onDelete={() => null}
        onSetOutput={() => null}
      />,
    )

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'Saved before close' } })
    fireEvent.click(screen.getByLabelText('关闭'))

    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
    act(() => pending.resolve(draft(5)))
    await act(async () => {
      await Promise.resolve()
    })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('restores dirty state after a rejected save so flush can retry', async () => {
    const rejected = deferred<PipelineDraftResponse>()
    const retried = deferred<PipelineDraftResponse>()
    const onSave = vi.fn()
      .mockReturnValueOnce(rejected.promise)
      .mockReturnValueOnce(retried.promise)
    const ref = createRef<NodeConfigDrawerHandle>()
    render(
      <NodeConfigDrawer
        ref={ref}
        pipelineId="pipeline-a"
        revision={1}
        node={nodeA}
        definition={definition}
        issues={[]}
        isSaving={false}
        onClose={() => undefined}
        onSave={onSave}
        onDelete={() => null}
        onSetOutput={() => null}
      />,
    )

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'Retry me' } })
    let first!: Promise<number | null>
    act(() => {
      first = ref.current!.flush()
    })
    act(() => rejected.reject(new Error('save failed')))
    await act(async () => {
      expect(await first).toBeNull()
    })

    let second!: Promise<number | null>
    act(() => {
      second = ref.current!.flush()
    })
    expect(onSave).toHaveBeenCalledTimes(2)
    act(() => retried.resolve(draft(6)))
    await act(async () => {
      expect(await second).toBe(6)
    })
  })

  it('drains an edit made during an in-flight save and returns the final revision', async () => {
    const first = deferred<PipelineDraftResponse>()
    const second = deferred<PipelineDraftResponse>()
    const onSave = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const ref = createRef<NodeConfigDrawerHandle>()
    render(
      <NodeConfigDrawer
        ref={ref}
        pipelineId="pipeline-a"
        revision={1}
        node={nodeA}
        definition={definition}
        issues={[]}
        isSaving={false}
        onClose={() => undefined}
        onSave={onSave}
        onDelete={() => null}
        onSetOutput={() => null}
      />,
    )

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'First save' } })
    act(() => vi.advanceTimersByTime(500))
    fireEvent.change(screen.getByDisplayValue('First save'), { target: { value: 'Second save' } })

    let flushed!: Promise<number | null>
    act(() => {
      flushed = ref.current!.flush()
    })
    act(() => first.resolve(draft(9)))
    await act(async () => {
      await Promise.resolve()
    })

    expect(onSave).toHaveBeenCalledTimes(2)
    expect(onSave).toHaveBeenLastCalledWith(
      'pipeline-a', nodeA.id, 'Second save', nodeA.config,
    )

    act(() => second.resolve(draft(12)))
    await act(async () => {
      await flushed
    })
    expect(await flushed).toBe(12)
  })

  it('continues draining the latest snapshot during unmount', async () => {
    const first = deferred<PipelineDraftResponse>()
    const second = deferred<PipelineDraftResponse>()
    const onSave = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const view = render(drawer('pipeline-a', nodeA, onSave))

    fireEvent.change(screen.getByDisplayValue('Filter A'), { target: { value: 'First save' } })
    act(() => vi.advanceTimersByTime(500))
    fireEvent.change(screen.getByDisplayValue('First save'), { target: { value: 'Second save' } })
    act(() => view.unmount())

    act(() => first.resolve(draft(9)))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(onSave).toHaveBeenCalledTimes(2)
    expect(onSave).toHaveBeenLastCalledWith(
      'pipeline-a', nodeA.id, 'Second save', nodeA.config,
    )
    act(() => second.resolve(draft(12)))
    await act(async () => {
      await Promise.resolve()
    })
  })
})
