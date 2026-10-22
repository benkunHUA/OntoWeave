import { ActionIcon, Button } from '@mantine/core'
import { CircleAlert, Eye, Info, Trash2, X } from 'lucide-react'
import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react'
import type {
  JsonObject,
  NodeDefinition,
  PipelineBuild,
  PipelineDraftResponse,
  PipelineIssue,
  PipelineNode,
} from '../../../../shared/types'
import {
  FIELD_LABELS,
  ISSUE_COPY,
  NODE_CONFIG_COPY,
} from '../../lib/constants'
import { TextField } from '../../../../shared/components/form/controls'
import { ConfigField } from './fields/ConfigField'
import { OutputBuildStatus } from './OutputBuildStatus'
import { OutputSelect } from './fields/OutputSelect'
import type { OutputDraftInput } from '../../lib/outputCommand'
import {
  applyDraftResponse,
  drawerStateFor,
  resetForNode,
  syncExternalConfig,
} from '../../lib/nodeConfigState'
import { useNodeConfigSave } from '../../hooks/useNodeConfigSave'

interface NodeConfigDrawerProps {
  pipelineId: string
  revision: number | null
  node: PipelineNode
  definition: NodeDefinition | null
  issues: PipelineIssue[]
  isSaving: boolean
  isReadOnly?: boolean
  focusField?: string | null
  onClose: () => void
  onPreview?: (nodeId: string, revision: number) => void
  onSave: (
    pipelineId: string,
    nodeId: string,
    name: string,
    config: JsonObject,
  ) => Promise<PipelineDraftResponse | null> | null
  onDelete: (nodeId: string) => Promise<unknown> | null
  onSetOutput: (
    nodeId: string,
    output: OutputDraftInput,
  ) => Promise<PipelineDraftResponse | null> | null
  latestBuild?: PipelineBuild | null
  onOpenBuildDetails?: (buildId: string) => void
}

export interface NodeConfigDrawerHandle {
  flush: () => Promise<number | null>
}

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

function sortIssues(issues: PipelineIssue[]): PipelineIssue[] {
  return [...issues].sort((left, right) => Number(right.blocking) - Number(left.blocking))
}

export const NodeConfigDrawer = forwardRef<NodeConfigDrawerHandle, NodeConfigDrawerProps>(function NodeConfigDrawer({
  pipelineId,
  revision,
  node,
  definition,
  issues,
  isSaving,
  isReadOnly = false,
  focusField = null,
  onClose,
  onPreview,
  onSave,
  onDelete,
  onSetOutput,
  latestBuild = null,
  onOpenBuildDetails,
}, ref) {
  const [drawer, setDrawer] = useState(() => drawerStateFor(node))
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [seenConfig, setSeenConfig] = useState(node.config)
  const bodyRef = useRef<HTMLDivElement | null>(null)
  const save = useNodeConfigSave({
    pipelineId,
    nodeId: node.id,
    revision,
    isReadOnly,
    state: drawer,
    setState: setDrawer,
    onSave,
  })

  if (drawer.nodeId !== node.id) {
    setSeenConfig(node.config)
    setDrawer((current) => resetForNode(current, node))
  } else if (seenConfig !== node.config) {
    setSeenConfig(node.config)
    if (!drawer.dirty) setDrawer((current) => syncExternalConfig(current, node))
  }

  useImperativeHandle(ref, () => ({ flush: save.flush }), [save.flush])

  useEffect(() => {
    if (!focusField) return
    const field = [...(bodyRef.current?.querySelectorAll<HTMLElement>('[data-config-field]') ?? [])]
      .find((element) => element.dataset.configField === focusField)
    const target = field?.querySelector<HTMLElement>('input, select, textarea, button')
    target?.scrollIntoView?.({ block: 'center' })
    target?.focus()
  }, [focusField, node.id])

  async function close() {
    await save.flush()
    onClose()
  }

  const properties = definition?.config_schema.properties ?? {}
  const widgets = definition?.ui_schema.fields ?? {}
  const isOutput = definition?.type === 'output'
  const orderedFields = Object.keys(properties).filter((field) => !(isOutput && field === 'outputId'))
  const sortedIssues = sortIssues(issues)

  function updateField(field: string, value: unknown) {
    setDrawer((current) => ({
      ...current,
      config: { ...current.config, [field]: value },
      dirty: true,
    }))
  }

  async function setOutput(nodeId: string, output: OutputDraftInput) {
    const response = await onSetOutput(nodeId, output)
    if (!response) return null
    save.setRevision(response.revision)
    setDrawer((current) => {
      const next = applyDraftResponse(current, node.id, response)
      return next
    })
    return response
  }

  return (
    <aside className="pipeline-drawer" aria-label={NODE_CONFIG_COPY.title}>
      <header className="pipeline-drawer__head">
        <div>
          <span className="eyebrow">{NODE_CONFIG_COPY.title}</span>
          <h2>{definition?.label ?? node.type}</h2>
        </div>
        <ActionIcon type="button" variant="subtle" color="gray" onClick={() => void close()} aria-label="关闭">
          <X size={16} />
        </ActionIcon>
      </header>

      <div className="pipeline-drawer__body" ref={bodyRef}>
        {!definition ? (
          <p className="pipeline-alert is-error">{NODE_CONFIG_COPY.unknownType}</p>
        ) : null}
        <TextField
          className="pipeline-config-field"
          label={NODE_CONFIG_COPY.name}
          value={drawer.name}
          disabled={isReadOnly}
          error={!drawer.name.trim() ? NODE_CONFIG_COPY.invalidName : undefined}
          onChange={(event) => {
            setDrawer((current) => ({ ...current, name: event.target.value, dirty: true }))
          }}
        />

        {orderedFields.map((field) => {
          const fieldIssues = sortedIssues.filter((issue) => issue.field === field)
          return (
            <div key={field} className="pipeline-config-field" data-config-field={field}>
              <span>{fieldLabel(field)}</span>
              <ConfigField
                field={field}
                widget={widgets[field]?.widget ?? 'text'}
                schema={properties[field]}
                value={drawer.config[field]}
                onChange={(value) => updateField(field, value)}
              />
              {fieldIssues.map((issue) => (
                <small key={`${issue.code}-${issue.message}`} className="pipeline-field-error">
                  {issue.message}
                </small>
              ))}
            </div>
          )
        })}

        {isOutput && definition ? (
          <>
            <OutputSelect
              nodeId={node.id}
              defaultName={node.name}
              outputId={typeof drawer.config.outputId === 'string' ? drawer.config.outputId : null}
              isReadOnly={isReadOnly}
              isSaving={isSaving}
              onSetOutput={setOutput}
            />
            <OutputBuildStatus
              build={latestBuild}
              outputId={typeof drawer.config.outputId === 'string' ? drawer.config.outputId : null}
              onOpenBuildDetails={onOpenBuildDetails}
            />
          </>
        ) : null}

        <section className="pipeline-issue-list">
          <h3>{NODE_CONFIG_COPY.issues}</h3>
          {sortedIssues.length ? sortedIssues.map((issue) => (
            <div key={`${issue.code}-${issue.message}`} className={`pipeline-issue ${issue.blocking ? 'is-blocking' : ''}`}>
              {issue.blocking ? <CircleAlert size={14} /> : <Info size={14} />}
              <div>
                <strong>{issue.blocking ? ISSUE_COPY.blocking : ISSUE_COPY.warning}</strong>
                <p>{issue.message}</p>
                {issue.hint ? <small>{issue.hint}</small> : null}
              </div>
            </div>
          )) : <p className="pipeline-issue-empty">{ISSUE_COPY.empty}</p>}
        </section>
      </div>

      <footer className="pipeline-drawer__foot">
        <span className="pipeline-save-state">
          {isSaving ? NODE_CONFIG_COPY.saving : NODE_CONFIG_COPY.saved}
        </span>
        <div className="pipeline-drawer__actions">
          {onPreview ? (
            <Button
              type="button"
              variant="default"
              disabled={isReadOnly}
              leftSection={<Eye size={14} />}
              onClick={() => {
                void save.flush().then((savedRevision) => {
                  if (savedRevision !== null) onPreview(node.id, savedRevision)
                })
              }}
            >
              {NODE_CONFIG_COPY.preview}
            </Button>
          ) : null}
          {confirmDelete ? (
            <div className="pipeline-delete-confirm">
              <span>{NODE_CONFIG_COPY.deleteConfirm}</span>
              <Button type="button" variant="default" onClick={() => setConfirmDelete(false)}>
                {NODE_CONFIG_COPY.cancel}
              </Button>
              <Button type="button" color="red" onClick={() => void onDelete(node.id)}>
                {NODE_CONFIG_COPY.delete}
              </Button>
            </div>
          ) : (
            <Button
              type="button"
              color="red"
              disabled={isReadOnly}
              leftSection={<Trash2 size={14} />}
              onClick={() => setConfirmDelete(true)}
            >
              {NODE_CONFIG_COPY.delete}
            </Button>
          )}
        </div>
      </footer>
    </aside>
  )
})
