import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import type { GraphEditOp, GraphSnapshot } from '../../../shared/types'
import { buildActionEdits } from '../lib/actionEdits'
import type { ActionDraft } from '../lib/actionDraft'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import { actionKeys } from './queryKeys'
import { useActionDetail } from './useActionDetail'

interface UseActionDrawerOptions {
  actionId: string | null
  snapshot: GraphSnapshot | null
  submitEdits: (edits: GraphEditOp[], options: DrawerSubmitOptions) => Promise<void>
}

/**
 * 动作抽屉的取数与保存态，与对象 / 关系抽屉同构：草稿比对在 `lib/actionEdits`，
 * 落库走工作区的 submitEdits。差别只在保存后要把动作详情重新取一遍——
 * 新参数、新规则这时才拿到后端 id，抽屉按最新定义重挂。
 */
export function useActionDrawer({ actionId, snapshot, submitEdits }: UseActionDrawerOptions) {
  const queryClient = useQueryClient()
  const { detail, isLoading, error: loadError } = useActionDetail(actionId)
  const [isSaving, setIsSaving] = useState(false)
  const [failure, setFailure] = useState<{ id: string; message: string } | null>(null)
  const [savedToken, setSavedToken] = useState(0)

  const save = useCallback(
    async (draft: ActionDraft, options: DrawerSubmitOptions) => {
      if (!detail) return
      const edits = buildActionEdits(detail, draft, snapshot)
      if (!edits.length) return
      const actionTypeId = detail.id
      setIsSaving(true)
      try {
        await submitEdits(edits, options)
        await queryClient.refetchQueries({ queryKey: actionKeys.detail(actionTypeId) })
        setSavedToken((token) => token + 1)
      } catch (reason) {
        setFailure({
          id: actionTypeId,
          message: reason instanceof Error ? reason.message : '保存失败',
        })
      } finally {
        setIsSaving(false)
      }
    },
    [detail, snapshot, submitEdits, queryClient],
  )

  const error = failure && actionId && failure.id === actionId ? failure.message : null

  return { detail, isLoading, loadError, isSaving, error, savedToken, save }
}
