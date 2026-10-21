import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamAgentRun } from './agent'

function emptyStreamResponse(): Response {
  return new Response(
    new ReadableStream({
      start(controller) {
        controller.close()
      },
    }),
    { status: 200 },
  )
}

describe('streamAgentRun', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends the turn id as a header without adding it to the JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(emptyStreamResponse())
    vi.stubGlobal('fetch', fetchMock)

    await streamAgentRun(
      'conversation-1',
      { text: '清洗订单' },
      () => {},
      undefined,
      'turn-1',
    )

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({ 'X-Turn-Id': 'turn-1' })
    expect(JSON.parse(String(init.body))).toEqual({ text: '清洗订单' })
  })
})
