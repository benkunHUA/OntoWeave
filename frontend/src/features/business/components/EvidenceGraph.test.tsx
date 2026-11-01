// @vitest-environment jsdom

import { cleanup, render, screen } from '../../../test/render'
import { afterEach, describe, expect, it } from 'vitest'
import { QUERY_COPY } from '../lib/constants'
import type { EvidenceGraph as EvidenceGraphData } from '../lib/evidenceGraph'
import { EvidenceGraph } from './EvidenceGraph'

const graph: EvidenceGraphData = {
  graphId: 'graph-1',
  graphName: '燃气业务图',
  objectTypes: [
    { id: 'owner', name: '业主', properties: [] },
    { id: 'meter', name: '燃气表', properties: [] },
  ],
  relations: [
    {
      id: 'rel-1',
      name: '拥有',
      sourceTypeId: 'owner',
      targetTypeId: 'meter',
      cardinality: 'one_to_many',
      forwardLabel: '拥有',
      reverseLabel: '属于',
    },
  ],
  nodes: [
    {
      id: 'owner:C-1',
      objectTypeId: 'owner',
      primaryKey: 'C-1',
      title: '王建国',
      properties: {},
    },
    {
      id: 'meter:M-1',
      objectTypeId: 'meter',
      primaryKey: 'M-1',
      title: '燃气表 10086',
      properties: {},
    },
  ],
  edges: [
    {
      id: 'rel-1:owner:C-1->meter:M-1',
      relationId: 'rel-1',
      source: 'owner:C-1',
      target: 'meter:M-1',
      label: '拥有',
    },
  ],
  warnings: ['truncated'],
}

afterEach(cleanup)

describe('EvidenceGraph', () => {
  it('renders instance nodes, relation labels and warnings', () => {
    render(<EvidenceGraph graph={graph} />)

    expect(screen.getByRole('img', { name: QUERY_COPY.evidenceGraphLabel })).toBeTruthy()
    expect(screen.getByText('王建国')).toBeTruthy()
    expect(screen.getByText('燃气表 10086')).toBeTruthy()
    expect(screen.getByText('拥有')).toBeTruthy()
    expect(screen.getByText(QUERY_COPY.evidenceGraphTruncated)).toBeTruthy()
  })

  it('shows an empty-edge note without inventing relations', () => {
    render(<EvidenceGraph graph={{ ...graph, edges: [], warnings: [] }} />)

    expect(screen.getByText(QUERY_COPY.evidenceGraphNoEdges)).toBeTruthy()
  })
})
