import { Button } from '@mantine/core'
import { Trash2 } from 'lucide-react'
import { RadioField } from '../../../../shared/components/form/controls'
import type { DeleteScope } from '../../../../shared/types'
import { DELETE_SCOPE_OPTIONS } from '../../lib/deleteScopes'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'

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
          <Button type="button" variant="default" onClick={onCancel}>
            取消
          </Button>
          <Button
            type="button"
            color="red"
            loading={isDeleting}
            leftSection={isDeleting ? undefined : <Trash2 size={15} />}
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? '删除中' : '删除'}
          </Button>
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
            <div key={option.value} className="view-dialog__choice">
              <RadioField
                name="delete-scope"
                value={option.value}
                checked={scope === option.value}
                onChange={() => onScopeChange(option.value)}
                label={
                  <span className="view-dialog__choice-copy">
                    <strong>{option.title}</strong>
                    <em>{option.hint}</em>
                  </span>
                }
              />
            </div>
          ))}
        </fieldset>
      </div>
    </DialogShell>
  )
}
