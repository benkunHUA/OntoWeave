import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelIndexRun,
  ensureOntologyConversation,
  getOntologyResourceDefinition,
  listObjects,
  listRelationInstances,
  putObjectBinding,
  validateRelationBinding,
} from './ontology'

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })
}

function mockFetch(value: unknown = {}) {
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(value)))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function calledUrl(fetchMock: ReturnType<typeof vi.fn>, index = 0): URL {
  return new URL(String(fetchMock.mock.calls[index][0]), 'http://localhost')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ontology API query serialization', () => {
  it('loads the full resource definition used by the mapping canvas', async () => {
    const fetchMock = mockFetch({ id: 'relation-1', kind: 'relation' })

    await getOntologyResourceDefinition('relation', 'relation-1')

    expect(String(fetchMock.mock.calls[0][0])).toBe(
      '/api/v1/ontology/resources/relation/relation-1',
    )
  })

  it('serializes object list filters as snake_case query parameters', async () => {
    const fetchMock = mockFetch({ items: [], total: 0, page: 2, page_size: 50 })

    await listObjects('type 1', {
      page: 2,
      page_size: 50,
      q: 'Ada Lovelace',
      filters: [{ attribute_id: 'attribute-1', op: 'contains', value: 'Ada' }],
    })

    const url = calledUrl(fetchMock)
    expect(decodeURIComponent(url.pathname)).toBe('/api/v1/ontology/object-types/type 1/objects')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('page_size')).toBe('50')
    expect(url.searchParams.get('q')).toBe('Ada Lovelace')
    expect(JSON.parse(url.searchParams.get('filters') ?? '[]')).toEqual([
      { attribute_id: 'attribute-1', op: 'contains', value: 'Ada' },
    ])
  })

  it('omits empty paging and filter values', async () => {
    const fetchMock = mockFetch({ items: [], total: 0, page: 1, page_size: 20 })

    await listObjects('type-1')
    await listRelationInstances('relation-1')

    expect(calledUrl(fetchMock).search).toBe('')
    expect(String(fetchMock.mock.calls[1][0])).toBe(
      '/api/v1/ontology/relation-types/relation-1/instances',
    )
  })

  it('uses snake_case for conversation and relation query parameters', async () => {
    const fetchMock = mockFetch({ id: 'conversation-1' })

    await ensureOntologyConversation('object_type', 'type-1')
    await listRelationInstances('relation-1', {
      page: 3,
      page_size: 15,
      from_primary_key: 'customer-1',
      to_primary_key: 'order-1',
    })

    expect(calledUrl(fetchMock).searchParams.get('resource_kind')).toBe('object_type')
    expect(calledUrl(fetchMock).searchParams.get('resource_id')).toBe('type-1')
    const relationUrl = calledUrl(fetchMock, 1)
    expect(relationUrl.searchParams.get('from_primary_key')).toBe('customer-1')
    expect(relationUrl.searchParams.get('to_primary_key')).toBe('order-1')
  })
})

describe('ontology API writes', () => {
  it('sends X-Operation-Id with binding saves and run cancellation', async () => {
    const fetchMock = mockFetch({ revision: 2 })
    const payload = {
      base_revision: 1,
      dataset_kind: 'SYNC' as const,
      dataset_id: 'dataset-1',
      mapping: {
        primary_key_attribute_id: 'attribute-1',
        title_key_attribute_id: null,
        attributes: [],
      },
    }

    await putObjectBinding('type-1', payload, 'operation-1')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.method).toBe('PUT')
    expect((init.headers as Record<string, string>)['X-Operation-Id']).toBe('operation-1')
    expect(JSON.parse(String(init.body))).toEqual(payload)

    fetchMock.mockResolvedValueOnce(jsonResponse({ status: 'CANCELLED' }))
    await cancelIndexRun('run-1', 'operation-2')
    const cancelInit = fetchMock.mock.calls[1][1] as RequestInit
    expect(cancelInit.method).toBe('POST')
    expect((cancelInit.headers as Record<string, string>)['X-Operation-Id']).toBe('operation-2')
  })

  it('derives relation validation from the supported preview endpoint', async () => {
    const fetchMock = mockFetch({ validation: { blocking: [], warnings: [], valid: true } })

    const result = await validateRelationBinding('relation-1', { dataset_id: 'dataset-1' })

    expect(result.valid).toBe(true)
    expect(calledUrl(fetchMock).pathname).toBe(
      '/api/v1/ontology/relation-types/relation-1/binding/preview',
    )
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      dataset_id: 'dataset-1',
      limit: 1,
    })
  })
})
