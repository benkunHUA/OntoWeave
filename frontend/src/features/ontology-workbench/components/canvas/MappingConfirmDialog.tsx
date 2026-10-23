import { CANVAS_COPY } from './copy'

interface MappingConfirmDialogProps {
  title: string
  body: string
  onCancel: () => void
  onConfirm: () => void
}

export function MappingConfirmDialog(props: MappingConfirmDialogProps) {
  return (
    <div className="mapping-confirm-backdrop" role="presentation">
      <section className="mapping-confirm" role="dialog" aria-modal="true" aria-labelledby="mapping-confirm-title">
        <h2 id="mapping-confirm-title">{props.title}</h2>
        <p>{props.body}</p>
        <div className="mapping-confirm__actions">
          <button type="button" className="secondary-button" onClick={props.onCancel}>{CANVAS_COPY.cancel}</button>
          <button type="button" className="primary-button" onClick={props.onConfirm}>{CANVAS_COPY.confirm}</button>
        </div>
      </section>
    </div>
  )
}
