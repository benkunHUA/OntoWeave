import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { getGraphPublication, publishGraph } from '../../../shared/api'
import { httpErrorText } from '../../../shared/api/errors'
import { PUBLISH_COPY } from '../lib/publication'
import { publicationKeys } from './queryKeys'

/** 图发布状态与发布动作；发布成功后直接更新缓存，不重取整块工作区。 */
export function useGraphPublication(
  graphId: string | undefined,
  reportMessage: (message: string) => void,
) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: publicationKeys.detail(graphId ?? ''),
    queryFn: () => getGraphPublication(graphId as string),
    enabled: Boolean(graphId),
    retry: false,
    staleTime: 30_000,
  })

  useEffect(() => {
    if (query.error) reportMessage(httpErrorText(query.error, PUBLISH_COPY.loadError))
  }, [query.error, reportMessage])

  const mutation = useMutation({
    mutationFn: (targetId: string) => publishGraph(targetId),
    onSuccess: (publication, targetId) => {
      queryClient.setQueryData(publicationKeys.detail(targetId), publication)
    },
    onError: (reason) => reportMessage(httpErrorText(reason, PUBLISH_COPY.publishError)),
  })

  const publish = useCallback(() => {
    if (graphId) mutation.mutate(graphId)
  }, [graphId, mutation])

  return {
    publication: query.data ?? null,
    isLoading: Boolean(graphId) && query.isPending,
    hasError: Boolean(query.error),
    isPublishing: mutation.isPending,
    publish,
  }
}
