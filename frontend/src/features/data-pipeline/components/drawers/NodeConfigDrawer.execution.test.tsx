// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { NodeDefinition, PipelineNode } from '../../../../shared/types'
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

describe('NodeConfigDrawer execution actions', () => {
  it('closes configuration through the preview action', () => {
    const onPreview = vi.fn()
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
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
    expect(onPreview).toHaveBeenCalledWith(node.id)
  })

  it('focuses the field reported by a preview issue', async () => {
    render(<NodeConfigDrawer
      pipelineId="pipeline-1"
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
})
