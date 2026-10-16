import { useQuery } from '@tanstack/react-query'
import { getActionDetail } from '../../../shared/api'
import { actionKeys } from './queryKeys'

/**
 * 动作详情（参数 / 规则 / 校验整棵树）。画布上的节点只有 brief，抽屉要的是全量定义，
 * 所以选中动作时才单独取一次。
 */
export function useActionDetail(actionId: string | null) {
  const query = useQuery({
    queryKey: actionKeys.detail(actionId ?? ''),
    queryFn: () => getActionDetail(actionId as string),
    enabled: Boolean(actionId),
  })
  return {
    detail: query.data ?? null,
    isLoading: query.isPending,
    error: query.error as Error | null,
  }
}
