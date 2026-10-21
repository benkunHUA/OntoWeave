import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type {
  PipelineCheckResult,
  PipelineDraftResponse,
  PipelinePreviewIssue,
} from '../../../shared/types'
import { outputNamesById, pipelineOutputs } from '../lib/outputs'
import {
  closePipelinePanel,
  openIssuePanel,
  openNodePanel,
  openPreviewPanel,
  type PipelinePanelState,
} from '../lib/panelState'
import { usePipelineBuild } from './usePipelineBuild'
import { usePipelinePreview } from './usePipelinePreview'

interface UsePipelineExecutionUiOptions {
  pipelineId: string | undefined
  draft: PipelineDraftResponse | null
  check: PipelineCheckResult | null
  onBuildSucceeded: () => void
}

export function usePipelineExecutionUi({
  pipelineId,
  draft,
  check,
  onBuildSucceeded,
}: UsePipelineExecutionUiOptions) {
  const [panel, setPanel] = useState<PipelinePanelState>(closePipelinePanel())
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const handledBuild = useRef<string | null>(null)
  const preview = usePipelinePreview({ pipelineId, draftRevision: draft?.revision ?? null })
  const build = usePipelineBuild(pipelineId)
  const closePreviewState = preview.close
  const runPreview = preview.run
  const openStoredBuild = build.open
  const setBuildDetailsOpen = build.setDetailsOpen

  const outputs = useMemo(() => pipelineOutputs(draft), [draft])
  const outputNames = useMemo(() => outputNamesById(draft), [draft])
  const selectedNode = useMemo(
    () => panel.mode === 'config'
      ? draft?.definition.nodes.find((node) => node.id === panel.nodeId) ?? null
      : null,
    [draft, panel],
  )
  const previewNode = useMemo(
    () => panel.mode === 'preview'
      ? draft?.definition.nodes.find((node) => node.id === panel.nodeId) ?? null
      : null,
    [draft, panel],
  )
  const selectedIssues = useMemo(() => {
    if (!check || !selectedNode) return []
    return [...check.blocking_issues, ...check.warnings]
      .filter((issue) => issue.node_id === selectedNode.id)
  }, [check, selectedNode])

  useEffect(() => {
    setPanel(closePipelinePanel())
    setScheduleOpen(false)
    closePreviewState()
  }, [closePreviewState, pipelineId])

  useEffect(() => {
    if (panel.mode === 'config' && !selectedNode) setPanel(closePipelinePanel())
    if (panel.mode === 'preview' && !previewNode) setPanel(closePipelinePanel())
  }, [panel.mode, previewNode, selectedNode])

  useEffect(() => {
    const current = build.build
    if (!current || current.status !== 'succeeded') return
    const key = `${current.id}:${current.status}`
    if (handledBuild.current === key) return
    handledBuild.current = key
    onBuildSucceeded()
  }, [build.build, onBuildSucceeded])

  const selectNode = useCallback((nodeId: string | null) => {
    if (!nodeId) {
      setPanel((current) => current.mode === 'config' ? closePipelinePanel() : current)
      return
    }
    closePreviewState()
    setBuildDetailsOpen(false)
    setScheduleOpen(false)
    setPanel((current) => openNodePanel(current, nodeId))
  }, [closePreviewState, setBuildDetailsOpen])

  const openPreview = useCallback((nodeId: string, revision?: number) => {
    setBuildDetailsOpen(false)
    setScheduleOpen(false)
    setPanel((current) => openPreviewPanel(current, nodeId))
    void runPreview(nodeId, revision)
  }, [runPreview, setBuildDetailsOpen])

  const openIssue = useCallback((issue: PipelinePreviewIssue) => {
    if (!issue.node_id) return
    const nodeId = issue.node_id
    closePreviewState()
    setBuildDetailsOpen(false)
    setScheduleOpen(false)
    setPanel((current) => openIssuePanel(current, nodeId, issue.field))
  }, [closePreviewState, setBuildDetailsOpen])

  const openBuildDetails = useCallback((buildId: string) => {
    closePreviewState()
    setPanel(closePipelinePanel())
    setScheduleOpen(false)
    openStoredBuild(buildId)
  }, [closePreviewState, openStoredBuild])

  const openSchedule = useCallback(() => {
    closePreviewState()
    setPanel(closePipelinePanel())
    setBuildDetailsOpen(false)
    setScheduleOpen(true)
  }, [closePreviewState, setBuildDetailsOpen])

  const closePanel = useCallback(() => setPanel(closePipelinePanel()), [])
  const closePreview = useCallback(() => {
    closePreviewState()
    setPanel(closePipelinePanel())
  }, [closePreviewState])

  return {
    preview,
    build,
    outputs,
    outputNames,
    selectedNode,
    selectedIssues,
    previewNode,
    focusField: panel.mode === 'config' ? panel.focusField : null,
    scheduleOpen,
    setScheduleOpen,
    selectNode,
    openPreview,
    openIssue,
    openBuildDetails,
    openSchedule,
    closePanel,
    closePreview,
  }
}
