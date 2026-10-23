// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '../../../../test/render'
import { CancelRunDialog } from './CancelRunDialog'
import { GenerateDialog } from './GenerateDialog'
import { RebindDialog } from './RebindDialog'
import { UnbindDialog } from './UnbindDialog'

describe('ontology confirmation dialogs', () => {
  afterEach(cleanup)

  it('shows generation facts and blocks double confirmation', () => {
    const onConfirm = vi.fn()
    render(
      <GenerateDialog
        resourceName="客户"
        datasetLabel="crm.customer"
        datasetVersionLabel="v12"
        revision={4}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    )
    expect(screen.getByText('crm.customer')).toBeTruthy()
    expect(screen.getByText('v12')).toBeTruthy()
    expect(screen.getByText('4')).toBeTruthy()
    expect(screen.getByText(/全量重建/)).toBeTruthy()

    const confirm = screen.getByRole('button', { name: /确认生成/ })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('explains that rebind only switches after a successful generation', () => {
    render(
      <RebindDialog
        resourceName="客户"
        oldDatasetLabel="crm.customer_v1"
        newDatasetLabel="crm.customer_v2"
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    )
    expect(screen.getByText('crm.customer_v1')).toBeTruthy()
    expect(screen.getByText('crm.customer_v2')).toBeTruthy()
    expect(screen.getByText(/生成成功后才切换/)).toBeTruthy()
  })

  it('requires an explicit acknowledgement before unbinding', () => {
    const onConfirm = vi.fn()
    render(
      <UnbindDialog
        resourceName="客户"
        objectCount={120}
        relationCount={32}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    )
    const confirm = screen.getByRole('button', { name: /解除绑定/ }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    expect(screen.getByText('120 条')).toBeTruthy()
    expect(screen.getByText('32 条')).toBeTruthy()

    fireEvent.click(screen.getByRole('checkbox'))
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('explains the different cancel behavior for queued and running runs', async () => {
    const { rerender } = render(
      <CancelRunDialog runId="run-1" status="QUEUED" onCancel={() => undefined} onConfirm={() => undefined} />,
    )
    expect(screen.getByText(/立即取消/)).toBeTruthy()

    rerender(
      <CancelRunDialog runId="run-1" status="RUNNING" onCancel={() => undefined} onConfirm={() => undefined} />,
    )
    expect(screen.getByText(/当前批次结束/)).toBeTruthy()
  })
})
