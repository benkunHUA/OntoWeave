import { describe, expect, it } from 'vitest'
import {
  closePipelinePanel,
  openIssuePanel,
  openNodePanel,
  openPreviewPanel,
} from './panelState'

describe('pipeline panel exclusivity', () => {
  it('switches from preview to configuration when a node is selected', () => {
    const preview = openPreviewPanel({ mode: 'none' }, 'node-a')

    expect(openNodePanel(preview, 'node-b')).toEqual({
      mode: 'config',
      nodeId: 'node-b',
      focusField: null,
    })
  })

  it('switches from configuration to preview without keeping a node drawer open', () => {
    const config = openNodePanel({ mode: 'none' }, 'node-a')

    expect(openPreviewPanel(config, 'node-a')).toEqual({
      mode: 'preview',
      nodeId: 'node-a',
    })
  })

  it('opens the owning node and focuses a field from a preview issue', () => {
    const preview = openPreviewPanel({ mode: 'none' }, 'node-b')

    expect(openIssuePanel(preview, 'node-a', 'amount')).toEqual({
      mode: 'config',
      nodeId: 'node-a',
      focusField: 'amount',
    })
    expect(closePipelinePanel()).toEqual({ mode: 'none' })
  })
})
