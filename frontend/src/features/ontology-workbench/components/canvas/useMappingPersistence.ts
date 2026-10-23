import { useCallback, useState } from 'react'
import { HttpError } from '../../../../shared/api/client'
import { httpErrorInfo, httpErrorText } from '../../../../shared/api/errors'
import {
  generateObjectBinding,
  generateRelationBinding,
  previewObjectBinding,
  previewRelationBinding,
  putObjectBinding,
  putRelationBinding,
  suggestObjectBinding,
  suggestRelationBinding,
  validateObjectBinding,
  validateRelationBinding,
} from '../../../../shared/api/ontology'
import type {
  DatasetColumn,
  MappingValidation,
  ObjectMapping,
  ObjectPreview,
  OntologyResourceDefinition,
  RelationMapping,
  RelationPreview,
} from '../../../../shared/types'
import {
  mergeObjectSuggestion,
  mergeRelationSuggestion,
  sanitizeObjectMapping,
  sanitizeRelationMapping,
  type MappingMode,
  type SuggestionMerge,
} from '../../lib/mapping'
import { draftFromBinding, fetchBinding } from './bindingDraft'
import { CANVAS_COPY } from './copy'
import type { DraftValue } from './draftState'

export interface MappingFeedback {
  tone: 'info' | 'error'
  message: string
}

export type MappingPreview = ObjectPreview | RelationPreview

interface MappingPersistenceOptions {
  mode: MappingMode
  resourceId: string
  definition: OntologyResourceDefinition | null
  columns: DatasetColumn[]
  value: DraftValue
  onSaved: (value: DraftValue) => void
  onReloaded: (value: DraftValue, validation: MappingValidation | null) => void
}

export function useMappingPersistence(options: MappingPersistenceOptions) {
  const { mode, resourceId, definition, columns, value, onSaved, onReloaded } = options
  const [feedback, setFeedback] = useState<MappingFeedback | null>(null)
  const [conflict, setConflict] = useState<string | null>(null)
  const [validation, setValidation] = useState<MappingValidation | null>(null)
  const [preview, setPreview] = useState<MappingPreview | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [isSuggesting, setIsSuggesting] = useState(false)
  const [isValidating, setIsValidating] = useState(false)
  const [isPreviewing, setIsPreviewing] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)

  const resetResults = useCallback(() => {
    setValidation(null)
    setPreview(null)
    setFeedback(null)
    setConflict(null)
  }, [])

  const replaceValidation = useCallback((next: MappingValidation | null) => {
    setValidation(next)
  }, [])

  const notify = useCallback((tone: MappingFeedback['tone'], message: string) => {
    setFeedback({ tone, message })
  }, [])

  const reportError = useCallback((message: string) => {
    setFeedback({ tone: 'error', message })
  }, [])

  const clearFeedback = useCallback(() => setFeedback(null), [])
  const clearPreview = useCallback(() => setPreview(null), [])

  const save = useCallback(async () => {
    if (!value.datasetKind || !value.datasetId || !definition) {
      setFeedback({ tone: 'error', message: CANVAS_COPY.chooseDatasetHint })
      return false
    }
    setIsSaving(true)
    setFeedback(null)
    const sentMapping = mode === 'object'
      ? sanitizeObjectMapping(value.mapping as ObjectMapping, definition)
      : sanitizeRelationMapping(value.mapping as RelationMapping)
    try {
      const payload = {
        base_revision: value.revision,
        dataset_kind: value.datasetKind,
        dataset_id: value.datasetId,
        mapping: sentMapping,
      }
      const result = mode === 'object'
        ? await putObjectBinding(resourceId, payload)
        : await putRelationBinding(resourceId, payload)
      const raw = result.draft_mapping ?? result.mapping
      const savedMapping = mode === 'object'
        ? raw && 'primary_key_attribute_id' in raw
          ? sanitizeObjectMapping(raw as ObjectMapping, definition)
          : sentMapping
        : raw && 'from_key_column' in raw ? sanitizeRelationMapping(raw as RelationMapping) : sentMapping
      onSaved({ ...value, mapping: savedMapping, revision: result.revision ?? value.revision + 1 })
      setValidation(result.validation ?? null)
      setFeedback({ tone: 'info', message: CANVAS_COPY.saveSuccess })
      return true
    } catch (reason) {
      const info = httpErrorInfo(reason, CANVAS_COPY.saveError)
      if (reason instanceof HttpError && reason.status === 409) {
        setConflict(CANVAS_COPY.conflict)
        try {
          const fresh = await fetchBinding(mode, resourceId)
          onReloaded(draftFromBinding(fresh, definition, mode), fresh?.validation ?? null)
          setValidation(fresh?.validation ?? null)
          setPreview(null)
        } catch {
          setConflict(CANVAS_COPY.conflictReloadFailed)
        }
      } else {
        setFeedback({ tone: 'error', message: info.message })
      }
      return false
    } finally {
      setIsSaving(false)
    }
  }, [definition, mode, onReloaded, onSaved, resourceId, value])

  const suggest = useCallback(async (): Promise<SuggestionMerge | null> => {
    if (!definition || !value.datasetKind || !value.datasetId) return null
    setIsSuggesting(true)
    setFeedback(null)
    try {
      const payload = {
        dataset_kind: value.datasetKind,
        dataset_id: value.datasetId,
        mapping: value.mapping,
      }
      const suggestion = mode === 'object'
        ? await suggestObjectBinding(resourceId, payload)
        : await suggestRelationBinding(resourceId, payload)
      const merged = mode === 'object'
        ? mergeObjectSuggestion(value.mapping as ObjectMapping, suggestion, definition, columns)
        : mergeRelationSuggestion(value.mapping as RelationMapping, suggestion, definition, columns)
      return merged
    } catch (reason) {
      setFeedback({ tone: 'error', message: httpErrorText(reason, CANVAS_COPY.suggestError) })
      return null
    } finally {
      setIsSuggesting(false)
    }
  }, [columns, definition, mode, resourceId, value])

  const validate = useCallback(async () => {
    if (!value.datasetKind || !value.datasetId) return
    setIsValidating(true)
    setFeedback(null)
    try {
      const payload = {
        dataset_kind: value.datasetKind,
        dataset_id: value.datasetId,
        mapping: value.mapping,
      }
      setValidation(mode === 'object'
        ? await validateObjectBinding(resourceId, payload)
        : await validateRelationBinding(resourceId, payload))
    } catch (reason) {
      setFeedback({ tone: 'error', message: httpErrorText(reason, CANVAS_COPY.validateError) })
    } finally {
      setIsValidating(false)
    }
  }, [mode, resourceId, value])

  const previewMapping = useCallback(async () => {
    if (!value.datasetKind || !value.datasetId) return
    setIsPreviewing(true)
    setFeedback(null)
    try {
      const payload = {
        dataset_kind: value.datasetKind,
        dataset_id: value.datasetId,
        mapping: value.mapping,
        limit: 20,
      }
      const result = mode === 'object'
        ? await previewObjectBinding(resourceId, payload)
        : await previewRelationBinding(resourceId, payload)
      setPreview(result)
      setValidation(result.validation)
    } catch (reason) {
      setFeedback({ tone: 'error', message: httpErrorText(reason, CANVAS_COPY.previewError) })
    } finally {
      setIsPreviewing(false)
    }
  }, [mode, resourceId, value])

  const generate = useCallback(async () => {
    setIsGenerating(true)
    setFeedback(null)
    try {
      const payload = { mapping_revision: value.revision }
      if (mode === 'object') await generateObjectBinding(resourceId, payload)
      else await generateRelationBinding(resourceId, payload)
      setFeedback({ tone: 'info', message: CANVAS_COPY.generated })
    } catch (reason) {
      setFeedback({ tone: 'error', message: httpErrorText(reason, CANVAS_COPY.generateError) })
    } finally {
      setIsGenerating(false)
    }
  }, [mode, resourceId, value.revision])

  return {
    feedback,
    conflict,
    validation,
    preview,
    isSaving,
    isSuggesting,
    isValidating,
    isPreviewing,
    isGenerating,
    save,
    suggest,
    validate,
    previewMapping,
    generate,
    resetResults,
    replaceValidation,
    notify,
    reportError,
    clearFeedback,
    clearPreview,
  }
}
