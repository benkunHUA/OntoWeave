import { describe, expect, it } from 'vitest'
import { buildDraftCommand } from './command'

describe('buildDraftCommand', () => {
  it('carries the current revision and operation id for set_output', () => {
    const operationId = '00000000-0000-4000-8000-000000000010'
    const command = buildDraftCommand(
      {
        type: 'set_output',
        output: {
          node_id: '00000000-0000-4000-8000-000000000001',
          name: 'orders_clean',
          write_mode: 'snapshot',
          primary_key_columns: ['id'],
          expectations: [],
        },
      },
      7,
      operationId,
    )

    expect(command.type).toBe('set_output')
    expect(command.base_revision).toBe(7)
    expect(command.operation_id).toBe(operationId)
    expect(command.output).toMatchObject({ name: 'orders_clean' })
  })
})
