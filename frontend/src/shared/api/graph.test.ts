import { afterEach, describe, expect, it, vi } from 'vitest'
import { getGraphPublication, publishGraph } from './graph'

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('graph publication API', () => {
  it('treats the unpublished response as an empty publication', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ message: 'graph has not been published' }, 400),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(getGraphPublication('graph-1')).resolves.toBeNull()
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/v1/graphs/graph-1/publication')
  })

  it('does not hide unrelated publication errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ message: '无权访问该图' }, 403)),
    )

    await expect(getGraphPublication('graph-1')).rejects.toThrow('无权访问该图')
  })

  it('publishes the graph with a POST request', async () => {
    const publication = {
      graph_id: 'graph-1',
      graph_card: '图：销售',
      embedding_model: 'text-embedding-v4',
      published_at: '2026-10-27T03:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(publication))
    vi.stubGlobal('fetch', fetchMock)

    await expect(publishGraph('graph-1')).resolves.toEqual(publication)
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST' })
  })
})
