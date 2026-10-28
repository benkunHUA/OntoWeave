import { ActionIcon, Badge, Button, Group } from '@mantine/core'
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
      <Group className="mapping-toolbar__status" gap="xs" aria-live="polite">
        <Badge color="gray" variant="light">{CANVAS_COPY.revision} {revision}</Badge>
        <Badge color={dirty ? 'yellow' : 'teal'} variant="light">
          {dirty ? CANVAS_COPY.unsaved : CANVAS_COPY.saved}
        </Badge>
        {blockingCount ? (
          <Badge color="red" variant="light">{CANVAS_COPY.blockingCount(blockingCount)}</Badge>
        ) : warningCount ? (
          <Badge color="yellow" variant="light">{CANVAS_COPY.warningCount(warningCount)}</Badge>
        ) : hasDataset ? (
          <Badge color="teal" variant="light">{CANVAS_COPY.valid}</Badge>
        ) : null}
      </Group>
      <Group className="mapping-toolbar__actions" gap="xs">
        <ActionIcon type="button" variant="subtle" color="gray" onClick={onUndo} disabled={!canUndo || busy} aria-label={CANVAS_COPY.undo} title={CANVAS_COPY.undo}>
          <Undo2 size={14} />
        </ActionIcon>
        <ActionIcon type="button" variant="subtle" color="gray" onClick={onRedo} disabled={!canRedo || busy} aria-label={CANVAS_COPY.redo} title={CANVAS_COPY.redo}>
          <Redo2 size={14} />
        </ActionIcon>
        <Button type="button" variant="default" size="xs" leftSection={<Sparkles size={14} />} onClick={onSuggest} disabled={!hasDataset || busy}>
          {CANVAS_COPY.suggest}
        </Button>
        <Button type="button" variant="default" size="xs" leftSection={<ShieldCheck size={14} />} onClick={onValidate} disabled={!hasDataset || busy}>
          {CANVAS_COPY.validate}
        </Button>
        <Button type="button" variant="default" size="xs" leftSection={<Eye size={14} />} onClick={onPreview} disabled={!hasDataset || busy}>
          {CANVAS_COPY.preview}
        </Button>
        <Button type="button" variant="default" size="xs" leftSection={<Save size={14} />} onClick={onSave} disabled={!canSave || busy}>
          {isSaving ? CANVAS_COPY.savePending : CANVAS_COPY.save}
        </Button>
        <Button
          type="button"
          size="xs"
          onClick={onGenerate}
          disabled={!canGenerate || busy}
          title={dirty ? CANVAS_COPY.saveBeforeGenerate : undefined}
          leftSection={<Rocket size={14} />}
        >
          {isGenerating
            ? CANVAS_COPY.generatePending
            : mode === 'object' ? CANVAS_COPY.generateObject : CANVAS_COPY.generateRelation}
        </Button>
      </Group>
    </div>
  )
}
