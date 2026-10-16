import { Handle, Position, type NodeProps } from '@xyflow/react'
import { CircleCheck, Sparkles, Zap } from 'lucide-react'
import type { ActionFlowNode } from '../../lib/flow'
import { STATUS_LABEL } from '../../lib/status'

/** 画布上的动作节点：做成一条「动作条」，和对象卡片明显区分。
 *  左右各一个出线端口，`involves` 虚线从离目标近的那一侧出去。 */
export function ActionNode({ data, selected }: NodeProps<ActionFlowNode>) {
  const classNames = [
    'action-node',
    data.draft ? 'is-draft' : '',
    data.ghosted ? 'is-ghosted' : '',
    data.highlighted ? 'is-highlighted' : '',
    data.dimmed ? 'is-dimmed' : '',
    selected ? 'is-selected' : '',
  ]
    .filter(Boolean)
    .join(' ')

  const drawn = data.status === 'confirmed' || data.status === 'approved'
  const StatusIcon = drawn ? CircleCheck : Sparkles

  return (
    <div className={classNames}>
      <Handle type="source" position={Position.Left} id="left" />
      <span className="action-node__icon">
        <Zap size={15} />
      </span>
      <div className="action-node__text">
        <strong>{data.label}</strong>
        <p className={data.description ? '' : 'muted-line'}>
          {data.description || '尚未补充描述'}
        </p>
      </div>
      <span className={`status-dot status-dot--${data.status}`}>
        <StatusIcon size={12} />
        {STATUS_LABEL[data.status]}
      </span>
      <Handle type="source" position={Position.Right} id="right" />
    </div>
  )
}
