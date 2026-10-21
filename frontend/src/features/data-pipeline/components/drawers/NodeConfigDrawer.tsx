import { CircleAlert, Info, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  JsonObject,
  NodeDefinition,
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

interface NodeConfigDrawerProps {
  node: PipelineNode
  definition: NodeDefinition | null
  issues: PipelineIssue[]
  isSaving: boolean
  isReadOnly?: boolean
  onClose: () => void
  onSave: (nodeId: string, name: string, config: JsonObject) => Promise<unknown> | null
  onDelete: (nodeId: string) => Promise<unknown> | null
}

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

function sortIssues(issues: PipelineIssue[]): PipelineIssue[] {
  return [...issues].sort((left, right) => Number(right.blocking) - Number(left.blocking))
}

export function NodeConfigDrawer({
  node,
  definition,
  issues,
  isSaving,
  isReadOnly = false,
  onClose,
  onSave,
  onDelete,
}: NodeConfigDrawerProps) {
  const [name, setName] = useState(node.name)
  const [config, setConfig] = useState<JsonObject>({ ...node.config })
  const [confirmDelete, setConfirmDelete] = useState(false)
  const dirtyRef = useRef(false)

  const saveNow = useCallback(() => {
    if (!dirtyRef.current || isReadOnly) return
    const trimmedName = name.trim()
    if (!trimmedName) return
    dirtyRef.current = false
    void onSave(node.id, trimmedName, config)
  }, [config, isReadOnly, name, node.id, onSave])

  useEffect(() => {
    if (!dirtyRef.current || isReadOnly) return
    const trimmedName = name.trim()
    if (!trimmedName) return
    const timer = window.setTimeout(saveNow, CONFIG_SAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [isReadOnly, name, saveNow])

  function close() {
    saveNow()
    onClose()
  }

  const properties = definition?.config_schema.properties ?? {}
  const widgets = definition?.ui_schema.fields ?? {}
  const orderedFields = Object.keys(properties)
  const sortedIssues = sortIssues(issues)

  function updateField(field: string, value: unknown) {
    dirtyRef.current = true
    setConfig((current) => ({ ...current, [field]: value }))
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

      <div className="pipeline-drawer__body">
        {!definition ? (
          <p className="pipeline-alert is-error">{NODE_CONFIG_COPY.unknownType}</p>
        ) : null}
        <label className="pipeline-config-field">
          <span>{NODE_CONFIG_COPY.name}</span>
          <input
            value={name}
            disabled={isReadOnly}
            onChange={(event) => {
              dirtyRef.current = true
              setName(event.target.value)
            }}
          />
          {!name.trim() ? <small className="pipeline-field-error">{NODE_CONFIG_COPY.invalidName}</small> : null}
        </label>

        {orderedFields.map((field) => {
          const fieldIssues = sortedIssues.filter((issue) => issue.field === field)
          return (
            <label key={field} className="pipeline-config-field">
              <span>{fieldLabel(field)}</span>
              <ConfigField
                field={field}
                widget={widgets[field]?.widget ?? 'text'}
                schema={properties[field]}
                value={config[field]}
                onChange={(value) => updateField(field, value)}
              />
              {fieldIssues.map((issue) => (
                <small key={`${issue.code}-${issue.message}`} className="pipeline-field-error">
                  {issue.message}
                </small>
              ))}
            </label>
          )
        })}

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
      </footer>
    </aside>
  )
}
