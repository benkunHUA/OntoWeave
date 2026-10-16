import type { GraphSnapshot } from '../../../../shared/types'
import { ACTION_COPY, ACTION_SECTION_COPY, RULE_KIND_OPTIONS } from '../../lib/actionConfig'
import type { ActionDraftController } from '../../hooks/useActionDraft'
import { ActionRuleRow } from './ActionRuleRow'
import { ActionSection } from './ActionSection'

interface ActionRuleListProps {
  controller: ActionDraftController
  snapshot: GraphSnapshot | null
}

/** 规则块：新规则默认从「新建对象」起步，种类在行里改。 */
export function ActionRuleList({ controller, snapshot }: ActionRuleListProps) {
  const { rules, parameters } = controller.draft
  const defaultKind = RULE_KIND_OPTIONS[0].value

  return (
    <ActionSection
      title={ACTION_SECTION_COPY.rules}
      count={rules.length}
      hint={ACTION_COPY.rulesHint}
      actionLabel="新增规则"
      onAdd={() => controller.addRule(defaultKind)}
      empty={!rules.length}
      emptyCopy={ACTION_COPY.rulesEmpty}
    >
      {rules.map((rule) => (
        <ActionRuleRow
          key={rule.key}
          rule={rule}
          parameters={parameters}
          snapshot={snapshot}
          issue={controller.issueFor(rule.key)}
          issueFor={controller.issueFor}
          onChange={(patch) => controller.updateRule(rule.key, patch)}
          onRemove={() => controller.removeRule(rule.key)}
          onAddAssignment={() => controller.addAssignment(rule.key)}
          onChangeAssignment={(key, patch) => controller.updateAssignment(rule.key, key, patch)}
          onRemoveAssignment={(key) => controller.removeAssignment(rule.key, key)}
        />
      ))}
    </ActionSection>
  )
}
