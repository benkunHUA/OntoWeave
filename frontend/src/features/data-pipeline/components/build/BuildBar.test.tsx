// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PipelineBuild } from '../../../../shared/types'
import { BuildBar } from './BuildBar'

const build: PipelineBuild = {
  id: 'build-1',
  pipeline_id: 'pipeline-1',
  pipeline_version_id: 'version-1',
  status: 'running',
  stage: 'checking_expectations',
  trigger: 'manual',
  error_code: null,
  error_message: null,
  started_at: '2026-10-21T10:00:00Z',
  finished_at: null,
  outputs: [],
}

afterEach(cleanup)

describe('BuildBar', () => {
  it('requires a confirmation that names the frozen revision and outputs', () => {
    const onBuild = vi.fn()
    render(<BuildBar
      revision={7}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      build={null}
      isCreating={false}
      error={null}
      onBuild={onBuild}
      onOpenDetails={() => undefined}
      onOpenSchedule={() => undefined}
    />)

    fireEvent.click(screen.getByRole('button', { name: '构建' }))

    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText(/修订 7/)).toBeTruthy()
    expect(screen.getByText('订单输出')).toBeTruthy()
    expect(screen.getByText('构建期间修改草稿不会进入本次构建')).toBeTruthy()
    expect(onBuild).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '确认构建' }))
    expect(onBuild).toHaveBeenCalledWith(7)
  })

  it('shows the active stage without creating another build from the status chip', () => {
    const onOpenDetails = vi.fn()
    const onBuild = vi.fn()
    render(<BuildBar
      revision={7}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      build={build}
      isCreating={false}
      error={null}
      onBuild={onBuild}
      onOpenDetails={onOpenDetails}
      onOpenSchedule={() => undefined}
    />)

    expect(screen.getByText('检查数据期望')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /查看构建详情/ }))

    expect(onOpenDetails).toHaveBeenCalledWith('build-1')
    expect(onBuild).not.toHaveBeenCalled()
  })
})
