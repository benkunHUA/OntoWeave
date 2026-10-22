// @vitest-environment jsdom

import { cleanup, renderHook, waitFor } from '../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../shared/api/client'
import { usePipelineList } from './usePipelineList'

const api = vi.hoisted(() => ({
  archivePipeline: vi.fn(),
  createPipeline: vi.fn(),
  listPipelines: vi.fn(),
  renamePipeline: vi.fn(),
}))
vi.mock('../../../shared/api', () => api)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('usePipelineList errors', () => {
  it('shows the structured message from a 500 response', async () => {
    api.listPipelines.mockRejectedValue(new HttpError(
      500,
      '{"code":"INTERNAL","message":"流程列表暂时不可用"}',
    ))
    const { result } = renderHook(() => usePipelineList())

    await waitFor(() => expect(result.current.error).toBe('流程列表暂时不可用'))
  })
})
