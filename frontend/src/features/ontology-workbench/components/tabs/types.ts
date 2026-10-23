import type { IndexRun, OntologyResource } from '../../../../shared/types'

export interface ResourceTabProps {
  resource: OntologyResource
}

export interface RunsTabProps extends ResourceTabProps {
  run: IndexRun | null
  isLoading: boolean
  error: string | null
  onRefresh: () => void
}
