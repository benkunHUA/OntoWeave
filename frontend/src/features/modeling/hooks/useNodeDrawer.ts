import { useCallback, useState } from 'react'
import type { GraphEditOp, GraphNode } from '../../../shared/types'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import { buildElementEdits, type NodeDraft } from '../lib/nodeDetails'

interface UseNodeDrawerOptions {
  node: GraphNode | null
  submitEdits: (edits: GraphEditOp[], options: DrawerSubmitOptions) => Promise<void>
}

/**
 * 对象抽屉的保存态。草稿比对在 `lib/nodeDetails`，落库走工作区的 submitEdits，
 * 这里只管「正在保存 / 上次失败的原因 / 保存完重挂抽屉」这三件事。
 */
export function useNodeDrawer({ node, submitEdits }: UseNodeDrawerOptions) {
  const [isSaving, setIsSaving] = useState(false)
  // 失败记在出错的元素上：换一个元素，旧错误自然不再显示，不用额外清理。
  const [failure, setFailure] = useState<{ id: string; message: string } | null>(null)
  // 保存成功后自增，用它当抽屉的 key：重新按服务端最新数据初始化草稿，
  // 新增字段行因此拿到真实 id，不会被重复提交。
  const [savedToken, setSavedToken] = useState(0)

  const save = useCallback(
    async (draft: NodeDraft, options: DrawerSubmitOptions) => {
      if (!node) return
      const edits = buildElementEdits(node, draft)
      if (!edits.length) return
      const elementId = node.id
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
    [node, submitEdits],
  )

  const error = failure && node && failure.id === node.id ? failure.message : null

  return { isSaving, error, savedToken, save }
}
