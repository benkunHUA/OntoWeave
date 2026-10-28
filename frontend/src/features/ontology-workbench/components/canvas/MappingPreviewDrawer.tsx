import { ActionIcon, Text, Title } from '@mantine/core'
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
          <Title order={2}>{CANVAS_COPY.previewTitle}</Title>
        </div>
        <ActionIcon type="button" variant="subtle" color="gray" onClick={onClose} aria-label={CANVAS_COPY.close}>
          <X size={15} />
        </ActionIcon>
      </header>
      <Text className="mapping-preview__note" size="xs" c="dimmed">{CANVAS_COPY.previewNoEffect}</Text>

      <section className="mapping-preview__section">
        <Title order={4}>{objectPreview ? CANVAS_COPY.previewObjectHead : CANVAS_COPY.previewRelationHead}</Title>
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
          <Text className="mapping-preview__empty" size="xs" c="dimmed">{CANVAS_COPY.previewEmpty}</Text>
        ) : null}
      </section>

      <section className="mapping-preview__section">
        <Title order={4}>{CANVAS_COPY.previewSkipped}</Title>
        {skippedReasons(preview).length ? (
          <ul className="mapping-preview__reasons">
            {skippedReasons(preview).map(([code, count]) => (
              <li key={code}><code>{code}</code><span>{count}</span></li>
            ))}
          </ul>
        ) : <Text className="mapping-preview__empty" size="xs" c="dimmed">0</Text>}
        {preview.skipped.map((item, index) => (
          <Text className="mapping-preview__skip" size="xs" c="dimmed" key={`${item.code}:${item.reference ?? index}`}>
            {item.message}{item.reference ? ` · ${item.reference}` : ''}
          </Text>
        ))}
      </section>
    </aside>
  )
}
