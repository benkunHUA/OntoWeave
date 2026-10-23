// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { OntologyProperty } from '../../../shared/types'
import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { FilterBar } from './FilterBar'

function property(overrides: Partial<OntologyProperty>): OntologyProperty {
  return {
    id: 'text-1',
    name: '名称',
    datatype: 'text',
    required: false,
    is_array: false,
    is_primary_key: false,
    is_title_key: false,
    enum_values: null,
    status: 'confirmed',
    ...overrides,
  }
}

const properties = [
  property({ id: 'text-1', name: '名称', datatype: 'text' }),
  property({ id: 'num-1', name: '金额', datatype: 'integer' }),
  property({ id: 'bool-1', name: '有效', datatype: 'boolean' }),
  property({ id: 'enum-1', name: '状态', datatype: 'enum', enum_values: ['ACTIVE', 'INACTIVE'] }),
  property({ id: 'array-1', name: '标签', is_array: true }),
]

describe('FilterBar', () => {
  afterEach(cleanup)

  it('adds removable conditions and narrows operators by type', () => {
    render(<FilterBar properties={properties} value={[]} onChange={() => undefined} />)

    fireEvent.click(screen.getByRole('button', { name: /添加条件/ }))
    const operator = screen.getByLabelText('操作符') as HTMLSelectElement
    expect(Array.from(operator.options).map((option) => option.value)).toContain('contains')

    fireEvent.change(screen.getByLabelText('属性'), { target: { value: 'num-1' } })
    expect(Array.from(operator.options).map((option) => option.value)).toContain('gte')
    expect(Array.from(operator.options).map((option) => option.value)).not.toContain('contains')
    expect(Array.from((screen.getByLabelText('属性') as HTMLSelectElement).options)
      .map((option) => option.value)).not.toContain('array-1')

    fireEvent.change(screen.getByLabelText('属性'), { target: { value: 'enum-1' } })
    expect(Array.from(operator.options).map((option) => option.value)).toEqual(['eq', 'ne'])

    fireEvent.click(screen.getByRole('button', { name: '删除条件' }))
    expect(screen.getByText('暂无筛选条件')).toBeTruthy()
  })

  it('blocks incomplete values and emits valid AND conditions', () => {
    const onChange = vi.fn()
    render(<FilterBar properties={properties} value={[]} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: /添加条件/ }))
    const apply = screen.getByRole('button', { name: '应用筛选' }) as HTMLButtonElement
    expect(apply.disabled).toBe(true)

    fireEvent.change(screen.getByLabelText('值'), { target: { value: 'Ada' } })
    expect(apply.disabled).toBe(false)
    fireEvent.click(apply)
    expect(onChange).toHaveBeenCalledWith([
      { attribute_id: 'text-1', op: 'eq', value: 'Ada' },
    ])
  })

  it('clears filters through the controlled callback', () => {
    const onChange = vi.fn()
    render(
      <FilterBar
        properties={properties}
        value={[{ attribute_id: 'text-1', op: 'eq', value: 'Ada' }]}
        onChange={onChange}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: '清空筛选' }))
    expect(onChange).toHaveBeenCalledWith([])
  })
})
