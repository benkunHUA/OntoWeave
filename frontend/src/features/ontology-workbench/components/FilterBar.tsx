import { Plus, SlidersHorizontal, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import type { FilterCondition, OntologyProperty } from '../../../shared/types'
import {
  FILTER_COPY,
  buildFilterConditions,
  createFilterDraft,
  filterInputType,
  isUnaryOperator,
  operatorsFor,
  type FilterDraft,
} from '../lib/filters'

interface FilterBarProps {
  properties: readonly OntologyProperty[]
  value: readonly FilterCondition[]
  onChange: (conditions: FilterCondition[]) => void
  disabled?: boolean
}

function nextOperator(property: OntologyProperty): FilterDraft {
  return createFilterDraft(property)
}

export function FilterBar({ properties, value, onChange, disabled = false }: FilterBarProps) {
  const filterable = useMemo(() => properties.filter((property) => !property.is_array), [properties])
  const [drafts, setDrafts] = useState<FilterDraft[]>([])
  const result = useMemo(() => buildFilterConditions(drafts, filterable), [drafts, filterable])
  const invalid = drafts.length > 0 && !result.valid
  const canAdd = !disabled && filterable.length > 0

  function addDraft() {
    const first = filterable[0]
    if (first) setDrafts((current) => [...current, nextOperator(first)])
  }

  function updateDraft(id: string, patch: Partial<FilterDraft>) {
    setDrafts((current) => current.map((draft) => (
      draft.id === id ? { ...draft, ...patch } : draft
    )))
  }

  function changeProperty(id: string, attributeId: string) {
    const property = filterable.find((item) => item.id === attributeId)
    if (!property) return
    updateDraft(id, {
      attributeId,
      op: nextOperator(property).op,
      value: property.datatype === 'boolean' ? 'true' : '',
    })
  }

  function changeOperator(id: string, op: FilterDraft['op']) {
    updateDraft(id, { op, ...(isUnaryOperator(op) ? { value: '' } : {}) })
  }

  function apply() {
    if (!result.valid) return
    onChange(result.conditions)
  }

  function clear() {
    setDrafts([])
    onChange([])
  }

  return (
    <section className="ontology-filter-bar" aria-label={FILTER_COPY.title}>
      <header className="ontology-filter-bar__head">
        <div>
          <strong><SlidersHorizontal size={14} /> {FILTER_COPY.title}</strong>
          <span>{FILTER_COPY.hint}</span>
        </div>
        <button type="button" className="secondary-button" onClick={addDraft} disabled={!canAdd}>
          <Plus size={13} /> {FILTER_COPY.add}
        </button>
      </header>

      {!drafts.length ? <p className="ontology-filter-bar__empty">{FILTER_COPY.empty}</p> : null}

      {drafts.length ? (
        <div className="ontology-filter-bar__rows">
          {drafts.map((draft) => {
            const property = filterable.find((item) => item.id === draft.attributeId)
            const datatype = property?.datatype ?? 'text'
            const inputType = filterInputType(datatype)
            const showInvalid = invalid && !result.valid && result.draftId === draft.id
            return (
              <div className={`ontology-filter-row${showInvalid ? ' is-invalid' : ''}`} key={draft.id}>
                <select
                  aria-label={FILTER_COPY.attribute}
                  value={draft.attributeId}
                  onChange={(event) => changeProperty(draft.id, event.target.value)}
                  disabled={disabled}
                >
                  {filterable.map((item) => (
                    <option key={item.id} value={item.id}>{item.name}</option>
                  ))}
                </select>
                <select
                  aria-label={FILTER_COPY.operator}
                  value={draft.op}
                  onChange={(event) => changeOperator(draft.id, event.target.value as FilterDraft['op'])}
                  disabled={disabled}
                >
                  {operatorsFor(datatype).map((operator) => (
                    <option key={operator.value} value={operator.value}>{operator.label}</option>
                  ))}
                </select>
                {isUnaryOperator(draft.op) ? (
                  <span className="ontology-filter-row__unary">无需填写值</span>
                ) : inputType === 'select' ? (
                  <select
                    aria-label={FILTER_COPY.value}
                    value={draft.value}
                    onChange={(event) => updateDraft(draft.id, { value: event.target.value })}
                    disabled={disabled}
                  >
                    <option value="true">{FILTER_COPY.true}</option>
                    <option value="false">{FILTER_COPY.false}</option>
                  </select>
                ) : (
                  <input
                    aria-label={FILTER_COPY.value}
                    type={inputType}
                    value={draft.value}
                    placeholder={FILTER_COPY.selectValue}
                    step={datatype === 'integer' ? 1 : datatype === 'decimal' ? 'any' : undefined}
                    onChange={(event) => updateDraft(draft.id, { value: event.target.value })}
                    disabled={disabled}
                  />
                )}
                <button
                  type="button"
                  className="icon-button icon-button--quiet"
                  aria-label={FILTER_COPY.remove}
                  onClick={() => setDrafts((current) => current.filter((item) => item.id !== draft.id))}
                  disabled={disabled}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            )
          })}
        </div>
      ) : null}

      {invalid ? <p className="ontology-filter-bar__error" role="alert">{result.reason}</p> : null}

      <footer className="ontology-filter-bar__actions">
        <button
          type="button"
          className="secondary-button"
          onClick={clear}
          disabled={disabled || (!drafts.length && !value.length)}
        >
          {FILTER_COPY.clear}
        </button>
        <button type="button" className="primary-button" onClick={apply} disabled={disabled || invalid}>
          {FILTER_COPY.apply}
        </button>
      </footer>
    </section>
  )
}
