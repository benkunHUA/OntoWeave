import { ChevronRight, Database, Hash } from 'lucide-react'
import { useState } from 'react'
import { QUERY_COPY } from '../lib/constants'
import type { QueryEvidenceItem } from '../lib/evidence'

interface QueryEvidenceProps {
  evidence: QueryEvidenceItem[]
}

/** task 结果里的结构化证据：默认收起，展开后看对象、步骤和索引版本。 */
export function QueryEvidence({ evidence }: QueryEvidenceProps) {
  const [open, setOpen] = useState(false)
  if (!evidence.length) return null

  return (
    <section className="query-evidence">
      <button
        type="button"
        className="query-evidence__toggle"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Database size={13} />
        <span>{QUERY_COPY.evidenceTitle}</span>
        <em>{evidence.length}</em>
        <ChevronRight size={13} className={open ? 'is-open' : ''} />
      </button>
      {open ? (
        <div className="query-evidence__list">
          {evidence.map((item, index) => (
            <article className="query-evidence__item" key={`${item.stepId}-${item.primaryKey}-${index}`}>
              <header>
                <strong>{item.title || item.primaryKey}</strong>
                <code>
                  <Hash size={11} />
                  {item.primaryKey}
                </code>
              </header>
              <dl>
                <div>
                  <dt>{QUERY_COPY.evidenceStep}</dt>
                  <dd>{item.stepId || '—'}</dd>
                </div>
                <div>
                  <dt>{QUERY_COPY.evidenceIndex}</dt>
                  <dd title={item.indexVersionId ?? undefined}>
                    {shortId(item.indexVersionId)}
                  </dd>
                </div>
                {Object.entries(item.properties)
                  .slice(0, 4)
                  .map(([key, value]) => (
                    <div key={key}>
                      <dt title={key}>{shortId(key)}</dt>
                      <dd>{formatValue(value)}</dd>
                    </div>
                  ))}
              </dl>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}

function shortId(value: string | null): string {
  if (!value) return '—'
  return value.length > 8 ? value.slice(0, 8) : value
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}
