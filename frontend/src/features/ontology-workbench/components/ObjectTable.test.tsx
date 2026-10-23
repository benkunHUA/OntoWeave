// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { ObjectTable } from './ObjectTable'

interface Row {
  id: string
  name: string
}

const rows: Row[] = [{ id: '1', name: 'Ada' }]
const columns = [
  { key: 'name', header: '名称', render: (row: Row) => row.name },
]

function table(overrides: Partial<Parameters<typeof ObjectTable<Row>>[0]> = {}) {
  return (
    <ObjectTable<Row>
      ariaLabel="对象列表"
      columns={columns}
      items={rows}
      total={25}
      page={1}
      pageSize={20}
      getRowKey={(row) => row.id}
      onPageChange={() => undefined}
      emptyTitle="暂无对象"
      emptyBody="生成后会显示在这里"
      {...overrides}
    />
  )
}

describe('ObjectTable', () => {
  afterEach(cleanup)

  it('shows total and supports page jumps', () => {
    const onPageChange = vi.fn()
    render(table({ onPageChange }))

    expect(screen.getByText('共 25 条')).toBeTruthy()
    const input = screen.getByLabelText('页码')
    fireEvent.change(input, { target: { value: '2' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('renders the version notice only when provided', () => {
    const { rerender } = render(table())
    expect(screen.queryByRole('status')).toBeNull()

    rerender(table({ versionNotice: '对象已更新，已回到第 1 页' }))
    expect(screen.getByRole('status').textContent).toContain('对象已更新')
  })

  it('covers loading, empty, error and retry states', () => {
    const onRetry = vi.fn()
    const { rerender } = render(table({ items: [], total: 0, error: '网络断开', onRetry }))
    expect(screen.getByRole('alert').textContent).toContain('网络断开')
    fireEvent.click(screen.getByRole('button', { name: /重试/ }))
    expect(onRetry).toHaveBeenCalled()

    rerender(table({ items: [], total: 0 }))
    expect(screen.getByText('暂无对象')).toBeTruthy()

    rerender(table({ items: [], total: 0, isLoading: true }))
    expect(screen.getByText(/正在读取数据/)).toBeTruthy()
  })
})
