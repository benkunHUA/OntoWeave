import { CircleAlert } from 'lucide-react'
import type { InterruptOption, MessageBlock } from '../../../shared/types'
import { INTERRUPT_HINT } from '../lib/interruptCopy'
import { InterruptDetails } from './InterruptDetails'

type InterruptBlock = Extract<MessageBlock, { kind: 'interrupt' }>

interface InterruptCardProps {
  block: InterruptBlock
  /** 这一块是不是当前等待回答的中断，只有它可点。 */
  pending: boolean
  /** 智能体正在跑，禁止重复提交。 */
  disabled: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 一问一答的确认卡片：问题 + 候选详情 + 选项。 */
export function InterruptCard({ block, pending, disabled, onAnswer }: InterruptCardProps) {
  return (
    <section className={`interrupt-card ${pending ? 'is-pending' : ''}`}>
      <div className="interrupt-card__question">
        <CircleAlert size={15} />
        <span>{block.question}</span>
      </div>
      <InterruptDetails details={block.details ?? []} />
      {block.options.length ? (
        <div className="interrupt-options">
          {block.options.map((option) => (
            <button
              type="button"
              key={option.label}
              onClick={() => onAnswer(option)}
              disabled={!pending || disabled}
            >
              {option.label}
            </button>
          ))}
        </div>
      ) : null}
      {pending ? (
        <p className="interrupt-card__hint">
          {block.freeText ? INTERRUPT_HINT.freeText : INTERRUPT_HINT.optionsOnly}
        </p>
      ) : null}
    </section>
  )
}
