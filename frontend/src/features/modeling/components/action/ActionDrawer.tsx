import { Text } from '@mantine/core'
import { Zap } from 'lucide-react'
import { useMemo } from 'react'
import { TextAreaField, TextField } from '../../../../shared/components/form/controls'
import type { ActionDetail, GraphSnapshot } from '../../../../shared/types'
import { ACTION_COPY } from '../../lib/actionConfig'
import { actionPendingDeleteIds, type ActionDraft } from '../../lib/actionDraft'
import type { DrawerSubmitOptions } from '../../lib/drawerSubmit'
import { useActionDraft } from '../../hooks/useActionDraft'
import { DrawerShell } from '../drawers/DrawerShell'
import { ActionCriteriaList } from './ActionCriteriaList'
import { ActionParameterList } from './ActionParameterList'
import { ActionRuleList } from './ActionRuleList'

interface ActionDrawerProps {
  detail: ActionDetail
  snapshot: GraphSnapshot | null
  /** 改动集里已标删、待确认的元素：先收起来，别当成还能编辑的草稿。 */
  pendingDeleteIds: ReadonlySet<string>
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSubmit: (draft: ActionDraft, options: DrawerSubmitOptions) => void
}

/**
 * 动作详情抽屉：基本信息 + 参数 + 规则 + 提交校验。
 *
 * 抽屉本身只管把草稿接到四块上；草稿的读写与清理在 `useActionDraft`，
 * 比对与落库在 `lib/actionEdits` 与工作区的 submitEdits。
 */
export function ActionDrawer({
  detail,
  snapshot,
  pendingDeleteIds,
  isSaving,
  error,
  onClose,
  onSubmit,
}: ActionDrawerProps) {
  const ownPendingDeletes = useMemo(
    () => actionPendingDeleteIds(detail, pendingDeleteIds),
    [detail, pendingDeleteIds],
  )
  const controller = useActionDraft(detail, snapshot, ownPendingDeletes)
  const { draft } = controller
  const canSave = controller.dirty && !controller.firstIssue && !isSaving
  const nameIssue = controller.issueFor('name')

  return (
    <DrawerShell
      className="drawer--action"
      ariaLabel="动作详情"
      icon={<Zap size={18} />}
      title={detail.name}
      status={detail.status}
      isSaving={isSaving}
      canSave={canSave}
      error={error}
      hint={controller.firstIssue}
      onClose={onClose}
      onSubmit={(options) => onSubmit(draft, options)}
    >
      <TextField
        label="名称"
        value={draft.name}
        placeholder="动作名称"
        error={nameIssue ?? undefined}
        onChange={(event) => controller.setName(event.target.value)}
      />

      <TextAreaField
        label="描述"
        value={draft.description}
        autosize
        minRows={3}
        maxRows={6}
        placeholder="这个动作做成什么事？"
        onChange={(event) => controller.setDescription(event.target.value)}
      />

      {ownPendingDeletes.size ? (
        <Text size="xs" c="dimmed">
          {ownPendingDeletes.size} 个元素{ACTION_COPY.pendingDeletes}
        </Text>
      ) : null}

      <ActionParameterList controller={controller} snapshot={snapshot} />
      <ActionRuleList controller={controller} snapshot={snapshot} />
      <ActionCriteriaList controller={controller} snapshot={snapshot} />
    </DrawerShell>
  )
}
