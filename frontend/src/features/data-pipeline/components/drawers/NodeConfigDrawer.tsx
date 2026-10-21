import { CircleAlert, Eye, Info, Trash2, X } from 'lucide-react'
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react'
import type {
  JsonObject,
  NodeDefinition,
  PipelineDraftResponse,
  PipelineIssue,
  PipelineNode,
} from '../../../../shared/types'
import {
  CONFIG_SAVE_DELAY_MS,
  FIELD_LABELS,
  ISSUE_COPY,
  NODE_CONFIG_COPY,
} from '../../lib/constants'
import { ConfigField } from './fields/ConfigField'
import { OutputSelect } from './fields/OutputSelect'
import type { OutputDraftInput } from '../../lib/outputCommand'
import {
  applyDraftResponse,
  configForSave,
  drawerStateFor,
  resetForNode,
  syncExternalConfig,
} from '../../lib/nodeConfigState'

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
}, ref) {
  const [drawer, setDrawer] = useState(() => drawerStateFor(node))
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [seenConfig, setSeenConfig] = useState(node.config)
  const dirtyRef = useRef(drawer.dirty)
  const revisionRef = useRef(revision)
  const savePromiseRef = useRef<Promise<number | null> | null>(null)
  const bodyRef = useRef<HTMLDivElement | null>(null)

  if (drawer.nodeId !== node.id) {
    setSeenConfig(node.config)
    setDrawer((current) => resetForNode(current, node))
  } else if (seenConfig !== node.config) {
    setSeenConfig(node.config)
    if (!drawer.dirty) setDrawer((current) => syncExternalConfig(current, node))
  }

  useEffect(() => {
    revisionRef.current = revision
  }, [revision])

  const saveNow = useCallback((): Promise<number | null> => {
    if (savePromiseRef.current) return savePromiseRef.current
    if (isReadOnly) return Promise.resolve(null)
    if (!dirtyRef.current) return Promise.resolve(revisionRef.current)
    const trimmedName = drawer.name.trim()
    if (!trimmedName) return Promise.resolve(null)
    dirtyRef.current = false
    setDrawer((current) => current.nodeId === node.id
      ? { ...current, dirty: false }
      : current)
    const promise = Promise.resolve(onSave(pipelineId, node.id, trimmedName, configForSave(drawer)))
      .then((response) => {
        if (!response) {
          dirtyRef.current = true
          setDrawer((current) => current.nodeId === node.id
            ? { ...current, dirty: true }
            : current)
          return null
        }
        revisionRef.current = response.revision
        return response.revision
      })
      .finally(() => {
        savePromiseRef.current = null
      })
    savePromiseRef.current = promise
    return promise
  }, [drawer, isReadOnly, node.id, onSave, pipelineId])

  useImperativeHandle(ref, () => ({ flush: saveNow }), [saveNow])

  useEffect(() => {
    if (!drawer.dirty || isReadOnly || !drawer.name.trim()) return
    const timer = window.setTimeout(() => void saveNow(), CONFIG_SAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [drawer.dirty, drawer.name, isReadOnly, saveNow])

  const flushRef = useRef(saveNow)
  useEffect(() => {
    flushRef.current = saveNow
    dirtyRef.current = drawer.dirty
  }, [drawer.dirty, saveNow])
  useEffect(() => () => void flushRef.current(), [node.id, pipelineId])

  useEffect(() => {
    if (!focusField) return
    const field = [...(bodyRef.current?.querySelectorAll<HTMLElement>('[data-config-field]') ?? [])]
      .find((element) => element.dataset.configField === focusField)
    const target = field?.querySelector<HTMLElement>('input, select, textarea, button')
    target?.scrollIntoView?.({ block: 'center' })
    target?.focus()
  }, [focusField, node.id])

  function close() {
    void saveNow()
    onClose()
  }

  const properties = definition?.config_schema.properties ?? {}
  const widgets = definition?.ui_schema.fields ?? {}
  const isOutput = definition?.type === 'output'
  const orderedFields = Object.keys(properties).filter((field) => !(isOutput && field === 'outputId'))
  const sortedIssues = sortIssues(issues)

  function updateField(field: string, value: unknown) {
    dirtyRef.current = true
    setDrawer((current) => ({
      ...current,
      config: { ...current.config, [field]: value },
      dirty: true,
    }))
  }

  async function setOutput(nodeId: string, output: OutputDraftInput) {
    const response = await onSetOutput(nodeId, output)
    if (!response) return null
    revisionRef.current = response.revision
    setDrawer((current) => {
      const next = applyDraftResponse(current, node.id, response)
      dirtyRef.current = next.dirty
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
        <button type="button" className="icon-button icon-button--quiet" onClick={close} aria-label="关闭">
          <X size={16} />
        </button>
      </header>

      <div className="pipeline-drawer__body" ref={bodyRef}>
        {!definition ? (
          <p className="pipeline-alert is-error">{NODE_CONFIG_COPY.unknownType}</p>
        ) : null}
        <label className="pipeline-config-field">
          <span>{NODE_CONFIG_COPY.name}</span>
          <input
            value={drawer.name}
            disabled={isReadOnly}
            onChange={(event) => {
              dirtyRef.current = true
              setDrawer((current) => ({ ...current, name: event.target.value, dirty: true }))
            }}
          />
          {!drawer.name.trim() ? <small className="pipeline-field-error">{NODE_CONFIG_COPY.invalidName}</small> : null}
        </label>

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
          <OutputSelect
            nodeId={node.id}
            defaultName={node.name}
            outputId={typeof drawer.config.outputId === 'string' ? drawer.config.outputId : null}
            isReadOnly={isReadOnly}
            isSaving={isSaving}
            onSetOutput={setOutput}
          />
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
            <button
              type="button"
              className="secondary-button"
              disabled={isReadOnly}
              onClick={() => {
                void saveNow().then((savedRevision) => {
                  if (savedRevision !== null) onPreview(node.id, savedRevision)
                })
              }}
            >
              <Eye size={14} />
              {NODE_CONFIG_COPY.preview}
            </button>
          ) : null}
          {confirmDelete ? (
            <div className="pipeline-delete-confirm">
              <span>{NODE_CONFIG_COPY.deleteConfirm}</span>
              <button type="button" className="secondary-button" onClick={() => setConfirmDelete(false)}>
                {NODE_CONFIG_COPY.cancel}
              </button>
              <button type="button" className="danger-button" onClick={() => void onDelete(node.id)}>
                {NODE_CONFIG_COPY.delete}
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="danger-button"
              disabled={isReadOnly}
              onClick={() => setConfirmDelete(true)}
            >
              <Trash2 size={14} />
              {NODE_CONFIG_COPY.delete}
            </button>
          )}
        </div>
      </footer>
    </aside>
  )
})
