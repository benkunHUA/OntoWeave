import {
  ActionIcon,
  Button,
  Group,
  NativeSelect,
  Paper,
  Stack,
  Text,
  TextInput,
} from '@mantine/core'
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
    <Paper component="section" className="ontology-filter-bar" aria-label={FILTER_COPY.title} withBorder radius="md" p="sm">
      <Group className="ontology-filter-bar__head" justify="space-between" align="flex-start">
        <Stack gap={2}>
          <Group gap={6}><SlidersHorizontal size={14} /><Text component="strong" size="sm">{FILTER_COPY.title}</Text></Group>
          <Text size="xs" c="dimmed">{FILTER_COPY.hint}</Text>
        </Stack>
        <Button
          type="button"
          variant="default"
          size="xs"
          leftSection={<Plus size={13} />}
          onClick={addDraft}
          disabled={!canAdd}
        >
          {FILTER_COPY.add}
        </Button>
      </Group>

      {!drafts.length ? <Text className="ontology-filter-bar__empty" size="xs" c="dimmed" mt="xs">{FILTER_COPY.empty}</Text> : null}

      {drafts.length ? (
        <Stack className="ontology-filter-bar__rows" gap="xs" mt="sm">
          {drafts.map((draft) => {
            const property = filterable.find((item) => item.id === draft.attributeId)
            const datatype = property?.datatype ?? 'text'
            const inputType = filterInputType(datatype)
            const showInvalid = invalid && !result.valid && result.draftId === draft.id
            return (
              <Group className={`ontology-filter-row${showInvalid ? ' is-invalid' : ''}`} key={draft.id} gap="xs" wrap="nowrap">
                <NativeSelect
                  aria-label={FILTER_COPY.attribute}
                  size="xs"
                  value={draft.attributeId}
                  onChange={(event) => changeProperty(draft.id, event.currentTarget.value)}
                  disabled={disabled}
                  data={filterable.map((item) => ({ value: item.id, label: item.name }))}
                />
                <NativeSelect
                  aria-label={FILTER_COPY.operator}
                  size="xs"
                  value={draft.op}
                  onChange={(event) => changeOperator(draft.id, event.currentTarget.value as FilterDraft['op'])}
                  disabled={disabled}
                  data={operatorsFor(datatype).map((operator) => ({ value: operator.value, label: operator.label }))}
                />
                {isUnaryOperator(draft.op) ? (
                  <Text className="ontology-filter-row__unary" size="xs" c="dimmed">无需填写值</Text>
                ) : inputType === 'select' ? (
                  <NativeSelect
                    aria-label={FILTER_COPY.value}
                    size="xs"
                    value={draft.value}
                    onChange={(event) => updateDraft(draft.id, { value: event.currentTarget.value })}
                    disabled={disabled}
                    data={[
                      { value: 'true', label: FILTER_COPY.true },
                      { value: 'false', label: FILTER_COPY.false },
                    ]}
                  />
                ) : (
                  <TextInput
                    aria-label={FILTER_COPY.value}
                    size="xs"
                    type={inputType}
                    value={draft.value}
                    placeholder={FILTER_COPY.selectValue}
                    onChange={(event) => updateDraft(draft.id, { value: event.currentTarget.value })}
                    disabled={disabled}
                  />
                )}
                <ActionIcon
                  type="button"
                  variant="subtle"
                  color="gray"
                  size="sm"
                  aria-label={FILTER_COPY.remove}
                  onClick={() => setDrafts((current) => current.filter((item) => item.id !== draft.id))}
                  disabled={disabled}
                >
                  <Trash2 size={14} />
                </ActionIcon>
              </Group>
            )
          })}
        </Stack>
      ) : null}

      {invalid ? <Text className="ontology-filter-bar__error" size="xs" c="red" role="alert" mt="xs">{result.reason}</Text> : null}

      <Group className="ontology-filter-bar__actions" justify="flex-end" gap="xs" mt="sm">
        <Button
          type="button"
          variant="default"
          size="xs"
          onClick={clear}
          disabled={disabled || (!drafts.length && !value.length)}
        >
          {FILTER_COPY.clear}
        </Button>
        <Button type="button" size="xs" onClick={apply} disabled={disabled || invalid}>
          {FILTER_COPY.apply}
        </Button>
      </Group>
    </Paper>
  )
}
