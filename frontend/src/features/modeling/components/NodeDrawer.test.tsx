// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { GraphNode } from '../../../shared/types'
import { NodeDrawer } from './NodeDrawer'

const node: GraphNode = {
  id: 'object-1',
  name: '客户',
  description: '购买商品的人',
  status: 'confirmed',
  properties: [
    {
      id: 'property-1',
      name: '客户名',
      datatype: 'text',
      required: true,
      is_array: false,
      is_primary_key: false,
      is_title_key: false,
      enum_values: null,
      status: 'confirmed',
    },
  ],
}

afterEach(cleanup)

function renderDrawer(onSubmit = vi.fn()) {
  render(
    <NodeDrawer
      node={node}
      pendingDeleteIds={new Set()}
      isSaving={false}
      error={null}
      onClose={() => undefined}
      onSubmit={onSubmit}
    />,
  )
  return onSubmit
}

describe('NodeDrawer', () => {
  it('renders the Mantine fields and adds a property row', () => {
    renderDrawer()

    expect(screen.getByLabelText('名称')).toBeTruthy()
    expect(screen.getByLabelText('描述')).toBeTruthy()
    expect(screen.getByDisplayValue('客户名')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '新增字段' }))

    expect(screen.getAllByPlaceholderText('字段名')).toHaveLength(2)
  })

  it('submits the edited draft after confirmation', () => {
    const onSubmit = renderDrawer()

    fireEvent.change(screen.getByLabelText('名称'), { target: { value: '终端客户' } })
    fireEvent.click(screen.getByRole('button', { name: '保存并确认' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ name: '终端客户' }),
      { confirm: true },
    )
  })
})
