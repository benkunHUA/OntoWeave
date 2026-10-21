// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { JsonObject, NodeDefinition, PipelineNode } from '../../../../shared/types'
import { NodeConfigDrawer } from './NodeConfigDrawer'

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

function drawer(
  pipelineId: string,
  node: PipelineNode,
  onSave: (
    pipelineId: string,
    nodeId: string,
    name: string,
    config: JsonObject,
  ) => Promise<unknown> | null,
) {
  return (
    <NodeConfigDrawer
      pipelineId={pipelineId}
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
  ) => Promise<unknown> | null
}) {
  const [open, setOpen] = useState(true)
  if (!open) return null
  return (
    <NodeConfigDrawer
      pipelineId="pipeline-a"
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
})
