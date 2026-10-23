import { X } from 'lucide-react'
import type { ObjectPreview, RelationPreview } from '../../../../shared/types'
import { CANVAS_COPY } from './copy'

interface MappingPreviewDrawerProps {
  preview: ObjectPreview | RelationPreview
  onClose: () => void
}

function skippedReasons(preview: ObjectPreview | RelationPreview) {
  return Object.entries(preview.skipped_reasons)
}

export function MappingPreviewDrawer({ preview, onClose }: MappingPreviewDrawerProps) {
  const objectPreview = 'rows' in preview
  return (
    <aside className="mapping-preview" aria-label={CANVAS_COPY.previewTitle}>
      <header className="mapping-preview__head">
        <div>
          <span className="eyebrow">{CANVAS_COPY.eyebrow}</span>
          <h2>{CANVAS_COPY.previewTitle}</h2>
        </div>
        <button type="button" className="icon-button icon-button--quiet" onClick={onClose} aria-label={CANVAS_COPY.close}>
          <X size={15} />
        </button>
      </header>
      <p className="mapping-preview__note">{CANVAS_COPY.previewNoEffect}</p>

      <section className="mapping-preview__section">
        <h3>{objectPreview ? CANVAS_COPY.previewObjectHead : CANVAS_COPY.previewRelationHead}</h3>
        {objectPreview && preview.rows.length ? (
          <div className="mapping-preview__rows">
            {preview.rows.map((row) => (
              <article key={row.primary_key}>
                <strong>{row.title || row.primary_key}</strong>
                <code>{row.primary_key}</code>
                <dl>
                  {Object.entries(row.properties).map(([key, value]) => (
                    <div key={key}><dt>{key}</dt><dd>{String(value ?? '—')}</dd></div>
                  ))}
                </dl>
              </article>
            ))}
          </div>
        ) : null}
        {!objectPreview && preview.pairs.length ? (
          <div className="mapping-preview__pairs">
            {preview.pairs.map((pair) => (
              <div key={`${pair.from_primary_key}:${pair.to_primary_key}`}>
                <span>{CANVAS_COPY.fromKey} {pair.from_primary_key}</span>
                <span>{CANVAS_COPY.toKey} {pair.to_primary_key}</span>
              </div>
            ))}
          </div>
        ) : null}
        {(!objectPreview && !preview.pairs.length) || (objectPreview && !preview.rows.length) ? (
          <p className="mapping-preview__empty">{CANVAS_COPY.previewEmpty}</p>
        ) : null}
      </section>

      <section className="mapping-preview__section">
        <h3>{CANVAS_COPY.previewSkipped}</h3>
        {skippedReasons(preview).length ? (
          <ul className="mapping-preview__reasons">
            {skippedReasons(preview).map(([code, count]) => (
              <li key={code}><code>{code}</code><span>{count}</span></li>
            ))}
          </ul>
        ) : <p className="mapping-preview__empty">0</p>}
        {preview.skipped.map((item, index) => (
          <p className="mapping-preview__skip" key={`${item.code}:${item.reference ?? index}`}>
            {item.message}{item.reference ? ` · ${item.reference}` : ''}
          </p>
        ))}
      </section>
    </aside>
  )
}
