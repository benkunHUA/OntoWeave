// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Pipeline } from '../../../shared/types'
import { PipelineListPane } from './PipelineListPane'

const created: Pipeline = {
  id: 'pipeline-1',
  conversation_id: 'conversation-1',
  name: '新数据整理流程',
  status: 'draft',
  draft_revision: null,
  current_version_id: null,
  current_version_number: null,
  current_version_draft_revision: null,
  created_at: null,
  updated_at: null,
}

afterEach(cleanup)

describe('PipelineListPane create', () => {
  it('creates directly instead of showing a name input', async () => {
    const onCreate = vi.fn().mockResolvedValue(created)
    render(
      <PipelineListPane
        pipelines={[]}
        activeId={null}
        isLoading={false}
        error={null}
        onCreate={onCreate}
        onDelete={vi.fn()}
        onSelect={vi.fn()}
        onHome={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /新建数据整理流程/ }))

    expect(screen.queryByRole('textbox', { name: '流程名称' })).toBeNull()
    await waitFor(() => expect(onCreate).toHaveBeenCalledTimes(1))
  })

  it('deletes a draft pipeline only after confirmation', async () => {
    const onDelete = vi.fn().mockResolvedValue(true)
    render(
      <PipelineListPane
        pipelines={[created]}
        activeId={created.id}
        isLoading={false}
        error={null}
        onCreate={vi.fn()}
        onDelete={onDelete}
        onSelect={vi.fn()}
        onHome={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: `删除流程：${created.name}` }))
    expect(onDelete).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '删除' }))

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(created))
  })

  it('offers deletion for published pipelines too', () => {
    render(
      <PipelineListPane
        pipelines={[{ ...created, status: 'active' }]}
        activeId={created.id}
        isLoading={false}
        error={null}
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onSelect={vi.fn()}
        onHome={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: `删除流程：${created.name}` })).toBeTruthy()
  })
})
