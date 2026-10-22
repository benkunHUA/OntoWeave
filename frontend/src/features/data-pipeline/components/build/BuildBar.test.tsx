// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor } from '../../../../test/render'
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
  log_uri: null,
  started_at: '2026-10-21T10:00:00Z',
  finished_at: null,
  outputs: [],
}

afterEach(cleanup)

const readyCheck = {
  pipeline_id: 'pipeline-1',
  revision: 7,
  stage: 'ready',
  blocking_issues: [],
  warnings: [],
  next_actions: [],
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('BuildBar', () => {
  it('requires a confirmation that names the frozen revision and outputs', async () => {
    const onBuild = vi.fn()
    render(<BuildBar
      revision={7}
      check={{ pipeline_id: 'pipeline-1', revision: 7, stage: 'ready', blocking_issues: [], warnings: [], next_actions: [] }}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      isWriting={false}
      build={null}
      isCreating={false}
      error={null}
      onPrepareBuild={async () => 7}
      onBuild={onBuild}
      onOpenDetails={() => undefined}
      onOpenSchedule={() => undefined}
    />)

    fireEvent.click(screen.getByRole('button', { name: '构建' }))

    await waitFor(() => expect(screen.getByRole('dialog')).toBeTruthy())
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
      check={{ pipeline_id: 'pipeline-1', revision: 7, stage: 'ready', blocking_issues: [], warnings: [], next_actions: [] }}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      isWriting={false}
      build={build}
      isCreating={false}
      error={null}
      onPrepareBuild={async () => 7}
      onBuild={onBuild}
      onOpenDetails={onOpenDetails}
      onOpenSchedule={() => undefined}
    />)

    expect(screen.getByText('检查数据期望')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /查看构建详情/ }))

    expect(onOpenDetails).toHaveBeenCalledWith('build-1')
    expect(onBuild).not.toHaveBeenCalled()
  })

  it('uses the revision returned by the pre-build flush', async () => {
    const pending = deferred<number | null>()
    const onBuild = vi.fn()
    render(<BuildBar
      revision={7}
      check={readyCheck}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      isWriting={false}
      build={null}
      isCreating={false}
      error={null}
      onPrepareBuild={() => pending.promise}
      onBuild={onBuild}
      onOpenDetails={() => undefined}
      onOpenSchedule={() => undefined}
    />)

    fireEvent.click(screen.getByRole('button', { name: '构建' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(onBuild).not.toHaveBeenCalled()

    await act(async () => pending.resolve(9))
    expect(screen.getByText(/修订 9/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '确认构建' }))
    expect(onBuild).toHaveBeenCalledWith(9)
  })

  it('blocks builds for unbound outputs, blocking checks, or a writing queue', () => {
    const common = {
      revision: 7,
      build: null,
      isCreating: false,
      error: null,
      onPrepareBuild: async () => 7,
      onBuild: () => undefined,
      onOpenDetails: () => undefined,
      onOpenSchedule: () => undefined,
    }
    const { rerender } = render(<BuildBar
      {...common}
      check={readyCheck}
      outputs={[{ nodeId: 'node-output', outputId: null, name: '未绑定输出' }]}
      isWriting={false}
    />)
    expect((screen.getByRole('button', { name: '构建' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByRole('button', { name: '构建' }).getAttribute('title'))
      .toBe('请先绑定全部输出节点')

    rerender(<BuildBar
      {...common}
      check={{ ...readyCheck, blocking_issues: [{ code: 'BLOCKED' }] as never }}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      isWriting={false}
    />)
    expect((screen.getByRole('button', { name: '构建' }) as HTMLButtonElement).disabled).toBe(true)

    rerender(<BuildBar
      {...common}
      check={readyCheck}
      outputs={[{ nodeId: 'node-output', outputId: 'output-1', name: '订单输出' }]}
      isWriting
    />)
    expect((screen.getByRole('button', { name: '构建' }) as HTMLButtonElement).disabled).toBe(true)
  })
})
