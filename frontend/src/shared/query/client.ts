import { QueryClient } from '@tanstack/react-query'

/**
 * 服务端数据的单一缓存。组件只声明「要什么」，重取与失效交给这里。
 *
 * retry 关掉：本地工具，失败了直接把错误交给用户，不静默重试。
 * refetchOnWindowFocus 关掉：切窗口回来重取整张图没有意义，实时性由改动集与运行流保证。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
})
