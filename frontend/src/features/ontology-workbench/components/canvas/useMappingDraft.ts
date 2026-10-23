import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import {
  getOntologyResourceDefinition,
  listBindableDatasetColumns,
  listBindableDatasets,
} from '../../../../shared/api/ontology'
import type {
  DatasetColumn,
  DatasetKind,
  ObjectMapping,
  OntologyDataset,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../../shared/types'
import {
  connectObjectAttribute,
  connectRelationEndpoint,
  dedupeIssues,
  localObjectIssues,
  localRelationIssues,
  normalizeConnectionHandles,
  primaryKeyProperty,
  removeObjectConnection,
  removeRelationConnection,
  type MappingMode,
} from '../../lib/mapping'
import { draftFromBinding, fetchBinding } from './bindingDraft'
import { CANVAS_COPY } from './copy'
import {
  draftReducer,
  initialDraftState,
  isDraftDirty,
  type CanvasMapping,
  type DraftValue,
} from './draftState'
import { useMappingPersistence } from './useMappingPersistence'

export type { MappingFeedback, MappingPreview } from './useMappingPersistence'

function placeholder(mode: MappingMode): DraftValue {
  return {
    datasetKind: null,
    datasetId: null,
    mapping: mode === 'object'
      ? { primary_key_attribute_id: '', title_key_attribute_id: null, attributes: [], required_attribute_ids: [] }
      : { from_key_column: '', from_key_datatype: 'text', to_key_column: '', to_key_datatype: 'text' },
    revision: 0,
  }
}

export function useMappingDraft(mode: MappingMode, resourceId: string) {
  const [state, dispatch] = useReducer(draftReducer, placeholder(mode), initialDraftState)
  const [definition, setDefinition] = useState<OntologyResourceDefinition | null>(null)
  const [datasets, setDatasets] = useState<OntologyDataset[]>([])
  const [columns, setColumns] = useState<DatasetColumn[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isColumnsLoading, setIsColumnsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const loadToken = useRef(0)

  const onSaved = useCallback((value: DraftValue) => dispatch({ type: 'saved', value }), [])
  const onReloaded = useCallback(
    (value: DraftValue) => dispatch({ type: 'load', value }),
    [],
  )
  const persistence = useMappingPersistence({
    mode,
    resourceId,
    definition,
    columns,
    value: state.value,
    onSaved,
    onReloaded,
  })
  const {
    resetResults, replaceValidation, reportError,
    notify, suggest: suggestRemote, save: saveRemote,
  } = persistence

  const load = useCallback(async () => {
    const token = ++loadToken.current
    setIsLoading(true)
    setError(null)
    resetResults()
    try {
      const [nextDefinition, binding, datasetPage] = await Promise.all([
        getOntologyResourceDefinition(mode === 'object' ? 'object_type' : 'relation', resourceId),
        fetchBinding(mode, resourceId),
        listBindableDatasets({ size: 200 }),
      ])
      if (token !== loadToken.current) return
      setDefinition(nextDefinition)
      setDatasets(datasetPage.items)
      dispatch({ type: 'load', value: draftFromBinding(binding, nextDefinition, mode) })
      replaceValidation(binding?.validation ?? null)
    } catch (reason) {
      if (token === loadToken.current) setError(httpErrorText(reason, CANVAS_COPY.definitionError))
    } finally {
      if (token === loadToken.current) setIsLoading(false)
    }
  }, [mode, replaceValidation, resetResults, resourceId])

  useEffect(() => {
    void load()
    return () => { loadToken.current += 1 }
  }, [load])

  useEffect(() => {
    if (!state.value.datasetKind || !state.value.datasetId) {
      setColumns([])
      setIsColumnsLoading(false)
      return
    }
    const token = ++loadToken.current
    setIsColumnsLoading(true)
    void listBindableDatasetColumns(state.value.datasetKind, state.value.datasetId)
      .then((next) => {
        if (token === loadToken.current) setColumns(next)
      })
      .catch((reason) => {
        if (token === loadToken.current) {
          setColumns([])
          reportError(httpErrorText(reason, CANVAS_COPY.columnsLoadError))
        }
      })
      .finally(() => {
        if (token === loadToken.current) setIsColumnsLoading(false)
      })
    return () => { loadToken.current += 1 }
  }, [reportError, state.value.datasetId, state.value.datasetKind])

  const applyValue = useCallback((next: DraftValue) => {
    dispatch({ type: 'edit', value: next })
    resetResults()
  }, [resetResults])

  const applyMapping = useCallback((mapping: CanvasMapping) => {
    applyValue({ ...state.value, mapping })
  }, [applyValue, state.value])

  const setDataset = useCallback((kind: DatasetKind, id: string) => {
    if (state.value.datasetKind === kind && state.value.datasetId === id) return
    const mapping = mode === 'object'
      ? { ...(state.value.mapping as ObjectMapping), attributes: [] }
      : { ...(state.value.mapping as RelationMapping), from_key_column: '', to_key_column: '' }
    applyValue({ ...state.value, datasetKind: kind, datasetId: id, mapping })
  }, [applyValue, mode, state.value])

  const setPrimaryKey = useCallback((attributeId: string) => {
    if (mode !== 'object') return
    applyMapping({ ...(state.value.mapping as ObjectMapping), primary_key_attribute_id: attributeId })
  }, [applyMapping, mode, state.value.mapping])

  const setTitleKey = useCallback((attributeId: string | null) => {
    if (mode !== 'object') return
    applyMapping({ ...(state.value.mapping as ObjectMapping), title_key_attribute_id: attributeId })
  }, [applyMapping, mode, state.value.mapping])

  const connect = useCallback((sourceHandle: string | null, targetHandle: string | null) => {
    const handles = normalizeConnectionHandles(sourceHandle, targetHandle)
    if (!handles || !definition) return
    const column = columns.find((item) => item.name === handles.column)
    if (!column) {
      reportError(CANVAS_COPY.fieldMissing)
      return
    }
    if (mode === 'object') {
      const propertyId = handles.left.startsWith('attribute:')
        ? handles.left.slice('attribute:'.length)
        : ''
      const property = definition.properties.find((item) => item.id === propertyId)
      if (!property) return
      const result = connectObjectAttribute(state.value.mapping as ObjectMapping, property, column)
      if (!result.ok) return reportError(result.message)
      applyMapping(result.mapping)
      return
    }
    const endpoint = handles.left === 'endpoint:from'
      ? 'from'
      : handles.left === 'endpoint:to' ? 'to' : null
    if (!endpoint) return
    const fromType = primaryKeyProperty(definition.source)?.datatype
    const toType = primaryKeyProperty(definition.target)?.datatype
    if (!fromType || !toType) return reportError(CANVAS_COPY.endpointsMissing)
    const result = connectRelationEndpoint(
      state.value.mapping as RelationMapping,
      endpoint,
      endpoint === 'from' ? fromType : toType,
      column,
    )
    if (!result.ok) return reportError(result.message)
    applyMapping(result.mapping)
  }, [applyMapping, columns, definition, mode, reportError, state.value.mapping])

  const removeEdge = useCallback((edgeId: string) => {
    if (mode === 'object') {
      applyMapping(removeObjectConnection(state.value.mapping as ObjectMapping, edgeId))
      return
    }
    if (edgeId === 'relation:from' || edgeId === 'relation:to') {
      applyMapping(removeRelationConnection(
        state.value.mapping as RelationMapping,
        edgeId === 'relation:from' ? 'from' : 'to',
      ))
    }
  }, [applyMapping, mode, state.value.mapping])

  const undo = useCallback(() => {
    dispatch({ type: 'undo' })
    resetResults()
  }, [resetResults])

  const redo = useCallback(() => {
    dispatch({ type: 'redo' })
    resetResults()
  }, [resetResults])

  const suggest = useCallback(async () => {
    const merged = await suggestRemote()
    if (!merged) return
    if (merged.added) applyMapping(merged.mapping)
    notify(
      'info',
      merged.added ? CANVAS_COPY.suggestAdded(merged.added) : CANVAS_COPY.suggestNone,
    )
  }, [applyMapping, notify, suggestRemote])

  const localIssues = useMemo(() => {
    if (!definition) return { blocking: [], warnings: [] }
    return mode === 'object'
      ? localObjectIssues(state.value.mapping as ObjectMapping, definition, columns)
      : localRelationIssues(state.value.mapping as RelationMapping, definition, columns)
  }, [columns, definition, mode, state.value.mapping])
  const blocking = useMemo(
    () => dedupeIssues([...localIssues.blocking, ...(persistence.validation?.blocking ?? [])]),
    [localIssues.blocking, persistence.validation],
  )
  const warnings = useMemo(
    () => dedupeIssues([...localIssues.warnings, ...(persistence.validation?.warnings ?? [])]),
    [localIssues.warnings, persistence.validation],
  )
  const dirty = isDraftDirty(state)
  const hasDataset = Boolean(state.value.datasetKind && state.value.datasetId)
  const relationComplete = mode === 'relation' &&
    Boolean((state.value.mapping as RelationMapping).from_key_column) &&
    Boolean((state.value.mapping as RelationMapping).to_key_column)

  return {
    state,
    definition,
    datasets,
    columns,
    isLoading,
    isColumnsLoading,
    error,
    conflict: persistence.conflict,
    feedback: persistence.feedback,
    validation: persistence.validation,
    preview: persistence.preview,
    blocking,
    warnings,
    dirty,
    hasDataset,
    canSave: hasDataset && !persistence.isSaving && (mode === 'object' || relationComplete),
    canGenerate: hasDataset && !dirty && !persistence.isSaving && blocking.length === 0 && !persistence.isGenerating,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0,
    isSaving: persistence.isSaving,
    isSuggesting: persistence.isSuggesting,
    isValidating: persistence.isValidating,
    isPreviewing: persistence.isPreviewing,
    isGenerating: persistence.isGenerating,
    setDataset,
    setPrimaryKey,
    setTitleKey,
    connect,
    removeEdge,
    undo,
    redo,
    save: saveRemote,
    suggest,
    validate: persistence.validate,
    previewMapping: persistence.previewMapping,
    generate: persistence.generate,
    clearFeedback: persistence.clearFeedback,
    clearPreview: persistence.clearPreview,
    reload: load,
  }
}
