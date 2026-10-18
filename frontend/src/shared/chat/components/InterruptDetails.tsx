import type { InterruptDetail } from '../../../shared/types'

/** 确认卡片里的候选详情（名称、描述、状态等），按 label/value 逐行展示。 */
export function InterruptDetails({ details }: { details: InterruptDetail[] }) {
  if (details.length === 0) return null
  return (
    <dl className="interrupt-details">
      {details.map((detail, index) => (
        <div className="interrupt-details__row" key={index}>
          <dt>{detail.label}</dt>
          <dd>{detail.value}</dd>
        </div>
      ))}
    </dl>
  )
}
