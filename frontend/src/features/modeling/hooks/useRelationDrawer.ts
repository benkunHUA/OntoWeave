import { useCallback, useState } from 'react'
import type { GraphEditOp, GraphRelation } from '../../../shared/types'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import {
  buildRelationEdits,
  deleteRelationEdit,
  type RelationDraft,
} from '../lib/relationDetails'

interface UseRelationDrawerOptions {
  relation: GraphRelation | null
  submitEdits: (edits: GraphEditOp[], options: DrawerSubmitOptions) => Promise<void>
}

/** 关系抽屉的保存态，与对象抽屉同构：保存 / 删除都产出编辑清单交给工作区。 */
export function useRelationDrawer({ relation, submitEdits }: UseRelationDrawerOptions) {
  const [isSaving, setIsSaving] = useState(false)
  const [failure, setFailure] = useState<{ id: string; message: string } | null>(null)
  const [savedToken, setSavedToken] = useState(0)

  const run = useCallback(
    async (elementId: string, edits: GraphEditOp[], options: DrawerSubmitOptions) => {
      setIsSaving(true)
      try {
        await submitEdits(edits, options)
        setSavedToken((token) => token + 1)
      } catch (reason) {
        setFailure({
          id: elementId,
          message: reason instanceof Error ? reason.message : '保存失败',
        })
      } finally {
        setIsSaving(false)
      }
    },
    [submitEdits],
  )

  const save = useCallback(
    async (draft: RelationDraft, options: DrawerSubmitOptions) => {
      if (!relation) return
      const edits = buildRelationEdits(relation, draft)
      if (!edits.length) return
      await run(relation.id, edits, options)
    },
    [relation, run],
  )

  const remove = useCallback(async () => {
    if (!relation) return
    // 删除走幽灵态：先标记，用户在确认条上定稿。
    await run(relation.id, [deleteRelationEdit(relation.id)], { confirm: false })
  }, [relation, run])

  const error = failure && relation && failure.id === relation.id ? failure.message : null

  return { isSaving, error, savedToken, save, remove }
}
