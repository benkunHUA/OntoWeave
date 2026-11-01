// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QUERY_COPY } from '../lib/constants'
import type { QueryEvidenceItem } from '../lib/evidence'
import { QueryEvidence } from './QueryEvidence'

const ontologyApi = vi.hoisted(() => ({ getOntologyResourceDefinition: vi.fn() }))

vi.mock('../../../shared/api/ontology', () => ontologyApi)

const evidence: QueryEvidenceItem[] = [
  {
    objectTypeId: 'type-order-line',
    objectTypeName: '订单明细',
    primaryKey: '1',
    title: '1',
    properties: {
      '213c8711-full': 1,
      '64835f5d-full': 1,
      'd6eda6d1-full': 8,
      '69f5c430-full': 2,
      'other-full': 1255.08,
    },
    propertyNames: {
      '213c8711-full': 'id',
      '64835f5d-full': 'order_id',
      'd6eda6d1-full': 'product_id',
      '69f5c430-full': 'quantity',
      'other-full': 'unit_price',
    },
    indexVersionId: '755c137e-1234-5678-90ab-cdef12345678',
  },
]

afterEach(cleanup)

describe('QueryEvidence', () => {
  it('shows readable object, field and metadata labels without truncating properties', () => {
    render(<QueryEvidence evidence={evidence} />)

    fireEvent.click(screen.getByRole('button', { name: new RegExp(QUERY_COPY.evidenceTitle) }))

    expect(screen.getByText('订单明细')).toBeTruthy()
    expect(screen.getByText('order_id')).toBeTruthy()
    expect(screen.getByText('unit_price')).toBeTruthy()
    expect(screen.getByText('1255.08')).toBeTruthy()
    expect(screen.getByText(`${QUERY_COPY.evidenceIndexVersion} 755c137e`)).toBeTruthy()
  })

  it('hydrates readable names for evidence saved before metadata was added', async () => {
    ontologyApi.getOntologyResourceDefinition.mockResolvedValue({
      id: 'legacy-type',
      name: '订单明细',
      properties: [{ id: 'legacy-quantity', name: 'quantity' }],
    })
    render(
      <QueryEvidence
        evidence={[{
          objectTypeId: 'legacy-type',
          objectTypeName: null,
          primaryKey: '1',
          title: null,
          properties: { 'legacy-quantity': 2 },
          propertyNames: {},
          indexVersionId: null,
        }]}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: new RegExp(QUERY_COPY.evidenceTitle) }))

    expect(await screen.findByText('订单明细')).toBeTruthy()
    expect(await screen.findByText('quantity')).toBeTruthy()
  })
})
