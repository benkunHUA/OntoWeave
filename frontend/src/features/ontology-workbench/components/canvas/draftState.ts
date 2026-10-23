import type { DatasetKind, ObjectMapping, RelationMapping } from '../../../../shared/types'

export type CanvasMapping = ObjectMapping | RelationMapping

export interface DraftValue {
  datasetKind: DatasetKind | null
  datasetId: string | null
  mapping: CanvasMapping
  revision: number
}

export interface DraftState {
  value: DraftValue
  saved: DraftValue
  past: DraftValue[]
  future: DraftValue[]
}

export type DraftAction =
  | { type: 'load'; value: DraftValue }
  | { type: 'edit'; value: DraftValue }
  | { type: 'saved'; value: DraftValue }
  | { type: 'undo' }
  | { type: 'redo' }

const UNDO_LIMIT = 50

export function sameDraft(left: DraftValue, right: DraftValue): boolean {
  return left.datasetKind === right.datasetKind &&
    left.datasetId === right.datasetId &&
    JSON.stringify(left.mapping) === JSON.stringify(right.mapping)
}

export function isDraftDirty(state: DraftState): boolean {
  return !sameDraft(state.value, state.saved)
}

export function initialDraftState(value: DraftValue): DraftState {
  return { value, saved: value, past: [], future: [] }
}

export function draftReducer(state: DraftState, action: DraftAction): DraftState {
  if (action.type === 'load') return initialDraftState(action.value)
  if (action.type === 'saved') {
    return { ...state, value: action.value, saved: action.value }
  }
  if (action.type === 'undo') {
    const previous = state.past.at(-1)
    if (!previous) return state
    return {
      value: previous,
      saved: state.saved,
      past: state.past.slice(0, -1),
      future: [state.value, ...state.future].slice(0, UNDO_LIMIT),
    }
  }
  if (action.type === 'redo') {
    const next = state.future[0]
    if (!next) return state
    return {
      value: next,
      saved: state.saved,
      past: [...state.past, state.value].slice(-UNDO_LIMIT),
      future: state.future.slice(1),
    }
  }
  if (sameDraft(state.value, action.value)) return state
  return {
    value: action.value,
    saved: state.saved,
    past: [...state.past, state.value].slice(-UNDO_LIMIT),
    future: [],
  }
}
