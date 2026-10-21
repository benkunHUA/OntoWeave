// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../../../shared/api/client'
import { DatasetPickerField } from './DatasetPickerField'

const api = vi.hoisted(() => ({ listPipelineInputDatasets: vi.fn() }))
vi.mock('../../../../../shared/api', () => api)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('DatasetPickerField errors', () => {
  it('shows a structured 422 message instead of the raw body', async () => {
    api.listPipelineInputDatasets.mockRejectedValue(new HttpError(
      422,
      '{"code":"DATASET_NOT_READY","message":"数据集尚未发布"}',
    ))
    render(<DatasetPickerField value="" onChange={() => undefined} />)

    await waitFor(() => expect(screen.getByText('数据集尚未发布')).toBeTruthy())
    expect(screen.queryByText(/\{"code"/)).toBeNull()
  })
})
