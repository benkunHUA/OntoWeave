import { Eye, Redo2, Rocket, Save, ShieldCheck, Sparkles, Undo2 } from 'lucide-react'
import type { MappingMode } from '../../lib/mapping'
import { CANVAS_COPY } from './copy'

interface MappingToolbarProps {
  mode: MappingMode
  revision: number
  dirty: boolean
  blockingCount: number
  warningCount: number
  hasDataset: boolean
  canUndo: boolean
  canRedo: boolean
  canSave: boolean
  canGenerate: boolean
  isSaving: boolean
  isSuggesting: boolean
  isValidating: boolean
  isPreviewing: boolean
  isGenerating: boolean
  onUndo: () => void
  onRedo: () => void
  onSuggest: () => void
  onValidate: () => void
  onPreview: () => void
  onSave: () => void
  onGenerate: () => void
}

export function MappingToolbar(props: MappingToolbarProps) {
  const {
    mode, revision, dirty, blockingCount, warningCount, hasDataset,
    canUndo, canRedo, canSave, canGenerate,
    isSaving, isSuggesting, isValidating, isPreviewing, isGenerating,
    onUndo, onRedo, onSuggest, onValidate, onPreview, onSave, onGenerate,
  } = props
  const busy = isSaving || isSuggesting || isValidating || isPreviewing || isGenerating
  return (
    <div className="mapping-toolbar">
      <div className="mapping-toolbar__status" aria-live="polite">
        <span>{CANVAS_COPY.revision} {revision}</span>
        <span className={dirty ? 'is-dirty' : 'is-saved'}>
          {dirty ? CANVAS_COPY.unsaved : CANVAS_COPY.saved}
        </span>
        {blockingCount ? (
          <span className="is-blocking">{CANVAS_COPY.blockingCount(blockingCount)}</span>
        ) : warningCount ? (
          <span className="is-warning">{CANVAS_COPY.warningCount(warningCount)}</span>
        ) : hasDataset ? (
          <span className="is-ok">{CANVAS_COPY.valid}</span>
        ) : null}
      </div>
      <div className="mapping-toolbar__actions">
        <button type="button" className="icon-button icon-button--quiet" onClick={onUndo} disabled={!canUndo || busy} aria-label={CANVAS_COPY.undo} title={CANVAS_COPY.undo}>
          <Undo2 size={14} />
        </button>
        <button type="button" className="icon-button icon-button--quiet" onClick={onRedo} disabled={!canRedo || busy} aria-label={CANVAS_COPY.redo} title={CANVAS_COPY.redo}>
          <Redo2 size={14} />
        </button>
        <button type="button" className="secondary-button" onClick={onSuggest} disabled={!hasDataset || busy}>
          <Sparkles size={14} />{CANVAS_COPY.suggest}
        </button>
        <button type="button" className="secondary-button" onClick={onValidate} disabled={!hasDataset || busy}>
          <ShieldCheck size={14} />{CANVAS_COPY.validate}
        </button>
        <button type="button" className="secondary-button" onClick={onPreview} disabled={!hasDataset || busy}>
          <Eye size={14} />{CANVAS_COPY.preview}
        </button>
        <button type="button" className="secondary-button" onClick={onSave} disabled={!canSave || busy}>
          <Save size={14} />{isSaving ? CANVAS_COPY.savePending : CANVAS_COPY.save}
        </button>
        <button
          type="button"
          className="primary-button"
          onClick={onGenerate}
          disabled={!canGenerate || busy}
          title={dirty ? CANVAS_COPY.saveBeforeGenerate : undefined}
        >
          <Rocket size={14} />
          {isGenerating
            ? CANVAS_COPY.generatePending
            : mode === 'object' ? CANVAS_COPY.generateObject : CANVAS_COPY.generateRelation}
        </button>
      </div>
    </div>
  )
}
