import { LoaderCircle, Trash2 } from 'lucide-react'
import type { DeleteScope } from '../../../../shared/types'
import { DELETE_SCOPE_OPTIONS } from '../../lib/deleteScopes'
import { DialogShell } from './DialogShell'

interface DeleteConversationDialogProps {
  conversationTitle: string | null
  scope: DeleteScope
  isDeleting: boolean
  onScopeChange: (scope: DeleteScope) => void
  onCancel: () => void
  onConfirm: () => void
}

export function DeleteConversationDialog({
  conversationTitle,
  scope,
  isDeleting,
  onScopeChange,
  onCancel,
  onConfirm,
}: DeleteConversationDialogProps) {
  return (
    <DialogShell
      icon={<Trash2 size={20} />}
      iconTone="danger"
      title="删除对话"
      subtitle="确认要删除的范围，操作无法撤销"
      onClose={onCancel}
      footer={
        <>
          <button type="button" className="secondary-button" onClick={onCancel}>
            取消
          </button>
          <button
            type="button"
            className="danger-button"
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? <LoaderCircle size={15} /> : <Trash2 size={15} />}
            {isDeleting ? '删除中' : '删除'}
          </button>
        </>
      }
    >
      <div className="view-dialog__body">
        <p>
          确定删除「{conversationTitle || '未命名对话'}」吗？图和改动记录会一并清掉，无法撤销。
        </p>
        <fieldset className="view-dialog__choices">
          <legend>图里的草稿对象和关系怎么处理</legend>
          {DELETE_SCOPE_OPTIONS.map((option) => (
            <label key={option.value} className="view-dialog__choice">
              <input
                type="radio"
                name="delete-scope"
                value={option.value}
                checked={scope === option.value}
                onChange={() => onScopeChange(option.value)}
              />
              <span>
                <strong>{option.title}</strong>
                <em>{option.hint}</em>
              </span>
            </label>
          ))}
        </fieldset>
      </div>
    </DialogShell>
  )
}
