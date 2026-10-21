// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  NodeDefinition,
  PipelineDraftResponse,
  PipelineNode,
} from '../../../../shared/types'
import { NodeConfigDrawer } from './NodeConfigDrawer'

const node: PipelineNode = {
  id: '00000000-0000-4000-8000-000000000001',
  type: 'filter',
  name: 'Filter',
  config: { condition: { kind: 'compare', field: 'amount', op: 'is_not_null' } },
  position: { x: 0, y: 0 },
}

const definition: NodeDefinition = {
  type: 'filter',
  version: 1,
  label: '数据筛选',
  category: 'transform',
  ports: [],
  config_schema: {
    type: 'object',
    properties: { condition: { type: 'object' } },
  },
  ui_schema: { fields: { condition: { widget: 'condition-tree' } } },
}

afterEach(cleanup)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function draft(revision: number): PipelineDraftResponse {
  return {
    pipeline_id: 'pipeline-1',
    revision,
    definition: { schemaVersion: 1, nodes: [node], edges: [] },
  }
}

describe('NodeConfigDrawer execution actions', () => {
  it('closes configuration through the preview action', async () => {
    const onPreview = vi.fn()
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
      revision={7}
      node={node}
      definition={definition}
      issues={[]}
      isSaving={false}
      onClose={() => undefined}
      onPreview={onPreview}
      onSave={() => null}
      onDelete={() => null}
      onSetOutput={() => null}
    />)

    fireEvent.click(screen.getByRole('button', { name: '预览' }))
    await waitFor(() => expect(onPreview).toHaveBeenCalledWith(node.id, 7))
  })

  it('focuses the field reported by a preview issue', async () => {
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
      revision={7}
      node={node}
      definition={definition}
      focusField="condition"
      issues={[]}
      isSaving={false}
      onClose={() => undefined}
      onSave={() => null}
      onDelete={() => null}
      onSetOutput={() => null}
    />)

    await waitFor(() => {
      const field = document.querySelector('[data-config-field="condition"]')
      expect(field?.contains(document.activeElement)).toBe(true)
    })
  })

  it('waits for a pending save and previews the returned revision', async () => {
    const pending = deferred<PipelineDraftResponse>()
    const onSave = vi.fn(() => pending.promise)
    const onPreview = vi.fn()
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
      revision={3}
      node={node}
      definition={definition}
      issues={[]}
      isSaving={false}
      onClose={() => undefined}
      onPreview={onPreview}
      onSave={onSave}
      onDelete={() => null}
      onSetOutput={() => null}
    />)

    fireEvent.change(screen.getByDisplayValue('Filter'), { target: { value: 'Filter saved' } })
    fireEvent.click(screen.getByRole('button', { name: '预览' }))

    expect(onSave).toHaveBeenCalledWith(
      'pipeline-1', node.id, 'Filter saved', node.config,
    )
    expect(onPreview).not.toHaveBeenCalled()

    await act(async () => pending.resolve(draft(9)))
    await waitFor(() => expect(onPreview).toHaveBeenCalledWith(node.id, 9))
  })

  it('does not preview when flushing the draft fails', async () => {
    const onSave = vi.fn()
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(draft(11))
    const onPreview = vi.fn()
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
      revision={3}
      node={node}
      definition={definition}
      issues={[]}
      isSaving={false}
      onClose={() => undefined}
      onPreview={onPreview}
      onSave={onSave}
      onDelete={() => null}
      onSetOutput={() => null}
    />)

    fireEvent.change(screen.getByDisplayValue('Filter'), { target: { value: 'Filter saved' } })
    fireEvent.click(screen.getByRole('button', { name: '预览' }))
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1))
    await act(async () => {
      await Promise.resolve()
    })

    expect(onPreview).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '预览' }))
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(onPreview).toHaveBeenCalledWith(node.id, 11))
  })
})
