import { CircleCheck, CircleX, LoaderCircle, Sparkles } from 'lucide-react'

interface ChangesetBarProps {
  /** 「新增 2 · 修改 1」式的改动摘要，非空。 */
  summary: string[]
  isConfirming: boolean
  onReject: () => void
  onConfirm: () => void
}

/** 底部确认条：列出本轮待确认的本体改动，给「撤销 / 确认」两个出口。 */
export function ChangesetBar({ summary, isConfirming, onReject, onConfirm }: ChangesetBarProps) {
  return (
    <section className="changeset-bar">
      <div>
        <span className="changeset-bar__icon">
          <Sparkles size={16} />
        </span>
        <div>
          <strong>待确认改动</strong>
          <span>{summary.join(' · ')}</span>
        </div>
      </div>
      <div className="changeset-bar__actions">
        <button type="button" className="secondary-button" onClick={onReject}>
          <CircleX size={15} />
          撤销
        </button>
        <button
          type="button"
          className="primary-button"
          onClick={onConfirm}
          disabled={isConfirming}
        >
          {isConfirming ? <LoaderCircle size={15} /> : <CircleCheck size={15} />}
          {isConfirming ? '确认中' : '确认'}
        </button>
      </div>
    </section>
  )
}
