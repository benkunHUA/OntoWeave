// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { GraphPublicationControl } from './GraphPublicationControl'
import { PUBLISH_COPY } from '../lib/publication'

const publication = {
  graph_id: 'graph-1',
  graph_card: '图：销售',
  embedding_model: 'text-embedding-v4',
  published_at: '2026-10-27T03:00:00Z',
}

afterEach(cleanup)

describe('GraphPublicationControl', () => {
  it('publishes an unpublished graph from the canvas toolbar', () => {
    const onPublish = vi.fn()
    render(
      <GraphPublicationControl
        publication={null}
        isLoading={false}
        hasError={false}
        isPublishing={false}
        blockedReason={null}
        onPublish={onPublish}
      />,
    )

    expect(screen.getByText(PUBLISH_COPY.unpublished)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: PUBLISH_COPY.publish }))
    expect(onPublish).toHaveBeenCalledOnce()
  })

  it('shows the publication time and offers republishing', () => {
    render(
      <GraphPublicationControl
        publication={publication}
        isLoading={false}
        hasError={false}
        isPublishing={false}
        blockedReason={null}
        onPublish={() => undefined}
      />,
    )

    expect(screen.getByText(/已发布/)).toBeTruthy()
    expect(screen.getByRole('button', { name: PUBLISH_COPY.republish })).toBeTruthy()
  })

  it('disables publishing while the changeset is unresolved', () => {
    render(
      <GraphPublicationControl
        publication={null}
        isLoading={false}
        hasError={false}
        isPublishing={false}
        blockedReason={PUBLISH_COPY.openChangeset}
        onPublish={() => undefined}
      />,
    )

    const button = screen.getByRole('button', { name: PUBLISH_COPY.publish })
    expect((button as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(PUBLISH_COPY.openChangeset)).toBeTruthy()
  })
})
