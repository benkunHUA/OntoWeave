import { describe, expect, it } from 'vitest'
import { buildSetOutputCommand } from './outputCommand'

describe('buildSetOutputCommand', () => {
  it('submits a set_output command with the current revision and operation id', () => {
    const command = buildSetOutputCommand(
      '00000000-0000-4000-8000-000000000001',
      {
        name: 'orders_clean',
        primaryKeyColumns: ['id'],
        expectations: [{ type: 'NOT_NULL', column: 'id' }],
      },
      8,
      '00000000-0000-4000-8000-000000000010',
      '00000000-0000-4000-8000-000000000020',
    )

    expect(command.type).toBe('set_output')
    expect(command.base_revision).toBe(8)
    expect(command.operation_id).toBe('00000000-0000-4000-8000-000000000010')
    expect(command.turn_id).toBe('00000000-0000-4000-8000-000000000020')
    expect(command.output).toEqual({
      node_id: '00000000-0000-4000-8000-000000000001',
      name: 'orders_clean',
      write_mode: 'snapshot',
      primary_key_columns: ['id'],
      expectations: [{ type: 'NOT_NULL', column: 'id' }],
    })
  })
})
