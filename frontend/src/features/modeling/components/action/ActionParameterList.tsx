import type { GraphSnapshot } from '../../../../shared/types'
import { ACTION_COPY, ACTION_SECTION_COPY } from '../../lib/actionConfig'
import type { ActionDraftController } from '../../hooks/useActionDraft'
import { ActionParameterRow } from './ActionParameterRow'
import { ActionSection } from './ActionSection'

interface ActionParameterListProps {
  controller: ActionDraftController
  snapshot: GraphSnapshot | null
}

/** 参数块：一行一个参数，可增可删。 */
export function ActionParameterList({ controller, snapshot }: ActionParameterListProps) {
  const { parameters } = controller.draft

  return (
    <ActionSection
      title={ACTION_SECTION_COPY.parameters}
      count={parameters.length}
      hint={ACTION_COPY.parametersHint}
      actionLabel="新增参数"
      onAdd={controller.addParameter}
      empty={!parameters.length}
      emptyCopy={ACTION_COPY.parametersEmpty}
    >
      {parameters.map((parameter) => (
        <ActionParameterRow
          key={parameter.key}
          parameter={parameter}
          snapshot={snapshot}
          issue={controller.issueFor(parameter.key)}
          onChange={(patch) => controller.updateParameter(parameter.key, patch)}
          onRemove={() => controller.removeParameter(parameter.key)}
        />
      ))}
    </ActionSection>
  )
}
