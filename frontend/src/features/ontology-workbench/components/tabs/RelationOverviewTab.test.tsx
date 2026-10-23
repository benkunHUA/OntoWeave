// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { OntologyResourceDefinition } from '../../../../shared/types'
import { cleanup, render, screen, waitFor } from '../../../../test/render'
import { relationDefinition, resource } from '../canvas/testFixtures'

const api = vi.hoisted(() => ({ getOntologyResourceDefinition: vi.fn() }))
vi.mock('../../../../shared/api/ontology', () => api)

import { RelationOverviewTab } from './RelationOverviewTab'

const foreignKeyDefinition: OntologyResourceDefinition = {
  ...relationDefinition,
  cardinality: 'one_to_many',
  foreign_key_property_id: 'foreign-key',
  foreign_key_property: {
    id: 'foreign-key', name: 'customer_id', datatype: 'text', required: false,
    is_array: false, is_primary_key: false, is_title_key: false,
    enum_values: null, status: 'confirmed',
  },
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getOntologyResourceDefinition.mockResolvedValue(foreignKeyDefinition)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('RelationOverviewTab', () => {
  it('shows the foreign key source property and target primary key without binding controls', async () => {
    render(<RelationOverviewTab resource={{ ...resource('relation'), relation_kind: 'FK' }} />)

    expect(await screen.findByText('外键属性：customer_id')).toBeTruthy()
    expect(screen.getByText('目标主键：id')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /保存|生成/ })).toBeNull()
    await waitFor(() => expect(api.getOntologyResourceDefinition).toHaveBeenCalledWith(
      'relation', 'relation-1',
    ))
  })
})
