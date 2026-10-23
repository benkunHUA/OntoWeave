// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../../shared/api/client'
import { act, cleanup, renderHook, waitFor } from '../../../../test/render'
import {
  ATTRIBUTE_ID,
  DATASET_ID,
  NAME_ID,
  columns,
  dataset,
  emptyObjectMapping,
  objectBinding,
  objectDefinition,
  relationDefinition,
} from './testFixtures'

const api = vi.hoisted(() => ({
  generateObjectBinding: vi.fn(),
  generateRelationBinding: vi.fn(),
  getObjectBinding: vi.fn(),
  getOntologyResourceDefinition: vi.fn(),
  getRelationBinding: vi.fn(),
  listBindableDatasetColumns: vi.fn(),
  listBindableDatasets: vi.fn(),
  previewObjectBinding: vi.fn(),
  previewRelationBinding: vi.fn(),
  putObjectBinding: vi.fn(),
  putRelationBinding: vi.fn(),
  suggestObjectBinding: vi.fn(),
  suggestRelationBinding: vi.fn(),
  validateObjectBinding: vi.fn(),
  validateRelationBinding: vi.fn(),
}))

vi.mock('../../../../shared/api/ontology', () => api)

import { useMappingDraft } from './useMappingDraft'

function notFound(): HttpError {
  return new HttpError(404, JSON.stringify({ code: 'NOT_FOUND' }))
}

async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

async function loadHook(mode: 'object' | 'relation' = 'object') {
  const view = renderHook(() => useMappingDraft(mode, mode === 'object' ? 'object-1' : 'relation-1'))
  await settle()
  return view
}

async function chooseDataset(result: { current: ReturnType<typeof useMappingDraft> }) {
  await act(async () => {
    result.current.setDataset('PIPELINE', DATASET_ID)
  })
  await waitFor(() => expect(result.current.columns).toHaveLength(columns.length))
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getOntologyResourceDefinition.mockResolvedValue(objectDefinition)
  api.getObjectBinding.mockRejectedValue(notFound())
  api.getRelationBinding.mockRejectedValue(notFound())
  api.listBindableDatasets.mockResolvedValue({ items: [dataset], total: 1, page: 1, size: 200 })
  api.listBindableDatasetColumns.mockResolvedValue(columns)
  api.putObjectBinding.mockResolvedValue({ revision: 5 })
  api.putRelationBinding.mockResolvedValue({ revision: 5 })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useMappingDraft', () => {
  it('enforces one-to-one object edges and supports undo/redo', async () => {
    const { result } = await loadHook()
    await chooseDataset(result)

    act(() => result.current.connect(`attribute:${ATTRIBUTE_ID}`, 'column:customer_id'))
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(1)

    act(() => result.current.connect(`attribute:${ATTRIBUTE_ID}`, 'column:customer_name'))
    expect(result.current.feedback).toMatchObject({ tone: 'error', message: expect.stringContaining('已经映射') })
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(1)

    act(() => result.current.connect(`attribute:${NAME_ID}`, 'column:customer_name'))
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(2)

    act(() => result.current.undo())
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(1)
    expect(result.current.canRedo).toBe(true)

    act(() => result.current.redo())
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(2)
  })

  it('saves the current revision with required attribute ids and marks the draft clean', async () => {
    api.getObjectBinding.mockReset()
    api.getObjectBinding.mockResolvedValue(objectBinding(4))
    const { result } = await loadHook()
    await waitFor(() => expect(result.current.columns).toHaveLength(columns.length))

    let saved = false
    await act(async () => { saved = await result.current.save() })

    expect(saved).toBe(true)
    expect(api.putObjectBinding).toHaveBeenCalledWith('object-1', expect.objectContaining({
      base_revision: 4,
      dataset_kind: 'PIPELINE',
      dataset_id: DATASET_ID,
      mapping: expect.objectContaining({
        primary_key_attribute_id: ATTRIBUTE_ID,
        required_attribute_ids: [ATTRIBUTE_ID, NAME_ID],
      }),
    }))
    expect(result.current.state.value.revision).toBe(5)
    expect(result.current.dirty).toBe(false)
  })

  it('reloads the server draft and clears undo history on a 409 conflict', async () => {
    const conflicting = objectBinding(9, {
      ...emptyObjectMapping,
      attributes: [{
        attribute_id: ATTRIBUTE_ID, column: 'customer_id', datatype: 'text',
        required: true, is_array: false, enum_values: null,
      }],
    })
    api.getObjectBinding.mockReset()
    api.getObjectBinding.mockResolvedValueOnce(objectBinding(1)).mockResolvedValueOnce(conflicting)
    api.putObjectBinding.mockRejectedValue(new HttpError(409, JSON.stringify({ code: 'MAPPING_REVISION_CONFLICT' })))
    const { result } = await loadHook()
    await waitFor(() => expect(result.current.columns).toHaveLength(columns.length))

    act(() => result.current.connect(`attribute:${NAME_ID}`, 'column:customer_name'))
    expect(result.current.canUndo).toBe(true)
    await act(async () => { await result.current.save() })

    expect(result.current.conflict).toContain('已被 Agent 或其他页面修改')
    expect(result.current.state.value.revision).toBe(9)
    expect((result.current.state.value.mapping as typeof emptyObjectMapping).attributes).toHaveLength(1)
    expect(result.current.canUndo).toBe(false)
    expect(result.current.dirty).toBe(false)
  })

  it('requires two distinct compatible columns for a many-to-many relation', async () => {
    api.getOntologyResourceDefinition.mockResolvedValue(relationDefinition)
    const { result } = await loadHook('relation')
    await chooseDataset(result)

    act(() => result.current.connect('endpoint:from', 'column:customer_id'))
    act(() => result.current.connect('endpoint:to', 'column:customer_id'))
    expect(result.current.feedback).toMatchObject({ tone: 'error' })
    expect(result.current.state.value.mapping).toMatchObject({
      from_key_column: 'customer_id',
      to_key_column: '',
    })

    act(() => result.current.connect('endpoint:to', 'column:order_id'))
    expect(result.current.state.value.mapping).toMatchObject({
      from_key_column: 'customer_id',
      to_key_column: 'order_id',
    })
    expect(result.current.blocking).toEqual([])
    expect(result.current.canGenerate).toBe(false)
  })
})
