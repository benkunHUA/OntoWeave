import { ArrowRight, KeyRound, Link2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import { getOntologyResourceDefinition } from '../../../../shared/api/ontology'
import type { OntologyResourceDefinition } from '../../../../shared/types'
import { PROPERTY_DATATYPE_LABELS } from '../../lib/mapping'
import { CANVAS_COPY } from '../canvas/copy'
import type { ResourceTabProps } from './types'

export function RelationOverviewTab({ resource }: ResourceTabProps) {
  const [loaded, setLoaded] = useState<{
    id: string
    definition: OntologyResourceDefinition | null
    error: string | null
  } | null>(null)

  useEffect(() => {
    let active = true
    void getOntologyResourceDefinition('relation', resource.id)
      .then((definition) => {
        if (active) setLoaded({ id: resource.id, definition, error: null })
      })
      .catch((reason) => {
        if (active) {
          setLoaded({
            id: resource.id,
            definition: null,
            error: httpErrorText(reason, CANVAS_COPY.overviewLoadError),
          })
        }
      })
    return () => { active = false }
  }, [resource.id])

  const current = loaded?.id === resource.id ? loaded : null
  const definition = current?.definition ?? null
  const error = current?.error ?? null
  if (error) {
    return <section className="relation-overview relation-overview--state"><p>{error}</p></section>
  }
  if (!definition) {
    return <section className="relation-overview relation-overview--state"><p>{CANVAS_COPY.definitionLoading}</p></section>
  }

  const sourcePrimary = definition.source?.properties.find((item) => item.is_primary_key)
  const targetPrimary = definition.target?.properties.find((item) => item.is_primary_key)
  const foreignKey = definition.foreign_key_property
  return (
    <section className="relation-overview" aria-label={CANVAS_COPY.overviewTitle}>
      <header className="relation-overview__head">
        <span className="eyebrow">{CANVAS_COPY.foreignKeyRelation}</span>
        <h2>{definition.name}</h2>
        <p>{CANVAS_COPY.overviewBody}</p>
      </header>
      <div className="relation-overview__flow">
        <article className="relation-overview__node">
          <span className="relation-overview__icon"><KeyRound size={16} /></span>
          <small>{CANVAS_COPY.sourceObject}</small>
          <strong>{definition.source?.name ?? definition.source_type_name ?? '—'}</strong>
          <span>{CANVAS_COPY.foreignKeyAttribute(foreignKey?.name ?? CANVAS_COPY.noForeignKey)}</span>
          <em>{foreignKey ? PROPERTY_DATATYPE_LABELS[foreignKey.datatype] : CANVAS_COPY.noLabel}</em>
        </article>
        <span className="relation-overview__arrow"><ArrowRight size={18} /><small>{definition.forward_label ?? '指向'}</small></span>
        <article className="relation-overview__node">
          <span className="relation-overview__icon"><Link2 size={16} /></span>
          <small>{CANVAS_COPY.targetObject}</small>
          <strong>{definition.target?.name ?? definition.target_type_name ?? '—'}</strong>
          <span>{CANVAS_COPY.targetPrimaryKey(targetPrimary?.name ?? CANVAS_COPY.noPrimaryKey)}</span>
          <em>{targetPrimary ? PROPERTY_DATATYPE_LABELS[targetPrimary.datatype] : CANVAS_COPY.noLabel}</em>
        </article>
      </div>
      <div className="relation-overview__meta">
        <span>{CANVAS_COPY.sourcePrimaryMeta(
          sourcePrimary?.name ?? CANVAS_COPY.noLabel,
          sourcePrimary ? PROPERTY_DATATYPE_LABELS[sourcePrimary.datatype] : CANVAS_COPY.noLabel,
        )}</span>
        <span>{CANVAS_COPY.cardinalityMeta(definition.cardinality ?? resource.cardinality ?? CANVAS_COPY.noLabel)}</span>
        <span>{CANVAS_COPY.statusMeta(definition.status)}</span>
      </div>
    </section>
  )
}
