// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '../../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { Position, type EdgeProps } from '@xyflow/react'
import { MappingEdge, type MappingFlowEdge } from './MappingEdge'

vi.mock('@xyflow/react', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@xyflow/react')>()
  return {
    ...actual,
    EdgeLabelRenderer: ({ children }: { children: ReactNode }) => (
      <foreignObject>{children}</foreignObject>
    ),
  }
})

const edgeProps = {
  id: 'object:name',
  source: 'ontology-attributes',
  target: 'dataset-columns',
  sourceX: 0,
  sourceY: 0,
  targetX: 100,
  targetY: 40,
  sourcePosition: Position.Right,
  targetPosition: Position.Left,
  data: { label: '名称 → name', onRemove: vi.fn() },
} satisfies EdgeProps<MappingFlowEdge>

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('MappingEdge remove control', () => {
  it('only shows on line hover and stays visible while moving onto the button', async () => {
    const { container } = render(
      <svg>
        <MappingEdge {...edgeProps} />
      </svg>,
    )
    const line = container.querySelector('g') as SVGGElement
    const remove = screen.getByRole('button', { name: '删除连线 名称 → name' })

    expect(remove.classList.contains('is-visible')).toBe(false)

    fireEvent.mouseEnter(line)
    expect(remove.classList.contains('is-visible')).toBe(true)

    fireEvent.mouseLeave(line)
    fireEvent.mouseEnter(remove)
    await act(async () => {
      vi.advanceTimersByTime(200)
    })
    expect(remove.classList.contains('is-visible')).toBe(true)

    fireEvent.mouseLeave(remove)
    await act(async () => {
      vi.advanceTimersByTime(200)
    })
    expect(remove.classList.contains('is-visible')).toBe(false)
  })
})
