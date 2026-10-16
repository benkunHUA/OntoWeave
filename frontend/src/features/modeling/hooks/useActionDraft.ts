import { useCallback, useMemo, useState } from 'react'
import type { ActionDetail, GraphSnapshot } from '../../../shared/types'
import { operatorOption } from '../lib/actionConfig'
import { isActionDraftDirty } from '../lib/actionEdits'
import {
  draftFromActionDetail,
  emptyOperandDraft,
  newAssignmentDraft,
  newConditionDraft,
  newParameterDraft,
  newRuleDraft,
  type ActionAssignmentDraft,
  type ActionConditionDraft,
  type ActionDraft,
  type ActionOperandDraft,
  type ActionParameterDraft,
  type ActionRuleDraft,
} from '../lib/actionDraft'
import { actionDraftIssues } from '../lib/actionValidation'

/** 抽屉里的一侧：左侧还是右侧操作数。 */
export type ConditionSide = 'left' | 'right'

export interface ActionDraftController {
  draft: ActionDraft
  /** 行问题：草稿元素的 key 对应哪句提示。 */
  issueFor: (key: string) => string | null
  /** 第一条待解决的问题；null 表示可以保存。 */
  firstIssue: string | null
  dirty: boolean
  setName: (name: string) => void
  setDescription: (description: string) => void
  addParameter: () => void
  updateParameter: (key: string, patch: Partial<ActionParameterDraft>) => void
  removeParameter: (key: string) => void
  addRule: (ruleKind: string) => void
  updateRule: (key: string, patch: Partial<ActionRuleDraft>) => void
  removeRule: (key: string) => void
  addAssignment: (ruleKey: string) => void
  updateAssignment: (ruleKey: string, key: string, patch: Partial<ActionAssignmentDraft>) => void
  removeAssignment: (ruleKey: string, key: string) => void
  addCondition: () => void
  updateCondition: (key: string, patch: Partial<ActionConditionDraft>) => void
  updateOperand: (key: string, side: ConditionSide, patch: Partial<ActionOperandDraft>) => void
  removeCondition: (key: string) => void
}

/**
 * 动作抽屉的草稿状态：整棵草稿的读写只在这里。
 *
 * 每个更新都顺手清理不相干的字段——换规则种类就换一组作用对象，换值来源就清掉别的来源，
 * 换判空运算符就丢掉右侧操作数。清理收在这里，组件只管发「用户改了什么」。
 */
export function useActionDraft(
  detail: ActionDetail,
  snapshot: GraphSnapshot | null,
  pendingDeleteIds?: ReadonlySet<string>,
): ActionDraftController {
  const [draft, setDraft] = useState<ActionDraft>(() =>
    draftFromActionDetail(detail, pendingDeleteIds),
  )

  const issues = useMemo(() => actionDraftIssues(draft, snapshot), [draft, snapshot])
  const dirty = useMemo(
    () => isActionDraftDirty(detail, draft, snapshot),
    [detail, draft, snapshot],
  )

  const setName = useCallback(
    (name: string) => setDraft((current) => ({ ...current, name })),
    [],
  )
  const setDescription = useCallback(
    (description: string) => setDraft((current) => ({ ...current, description })),
    [],
  )

  const addParameter = useCallback(() => {
    setDraft((current) => ({
      ...current,
      parameters: [...current.parameters, newParameterDraft()],
    }))
  }, [])

  const updateParameter = useCallback((key: string, patch: Partial<ActionParameterDraft>) => {
    setDraft((current) => ({
      ...current,
      parameters: current.parameters.map((parameter) => {
        if (parameter.key !== key) return parameter
        // 两组列互斥：换成对象参数就清掉类型与取值，换回值参数就清掉目标类型。
        if (patch.kind && patch.kind !== parameter.kind) {
          const cleared =
            patch.kind === 'object_ref'
              ? { datatype: 'text', enumValues: [], defaultValue: '' }
              : { targetObjectTypeId: '' }
          return { ...parameter, ...cleared, ...patch }
        }
        return { ...parameter, ...patch }
      }),
    }))
  }, [])

  const removeParameter = useCallback((key: string) => {
    setDraft((current) => {
      const removed = current.parameters.find((parameter) => parameter.key === key)
      return {
        ...current,
        parameters: current.parameters.filter((parameter) => parameter.key !== key),
        removedParameterIds: removed?.id
          ? [...current.removedParameterIds, removed.id]
          : current.removedParameterIds,
      }
    })
  }, [])

  const addRule = useCallback((ruleKind: string) => {
    setDraft((current) => ({ ...current, rules: [...current.rules, newRuleDraft(ruleKind)] }))
  }, [])

  const updateRule = useCallback((key: string, patch: Partial<ActionRuleDraft>) => {
    setDraft((current) => ({
      ...current,
      rules: current.rules.map((rule) => {
        if (rule.key !== key) return rule
        // 换种类等于换语义：作用对象与两端参数全部重来，赋值也一并清空。
        if (patch.ruleKind && patch.ruleKind !== rule.ruleKind) {
          return { ...rule, ...newRuleDraft(patch.ruleKind) }
        }
        return { ...rule, ...patch }
      }),
    }))
  }, [])

  const removeRule = useCallback((key: string) => {
    setDraft((current) => {
      const removed = current.rules.find((rule) => rule.key === key)
      return {
        ...current,
        rules: current.rules.filter((rule) => rule.key !== key),
        removedRuleIds: removed?.id
          ? [...current.removedRuleIds, removed.id]
          : current.removedRuleIds,
      }
    })
  }, [])

  const addAssignment = useCallback((ruleKey: string) => {
    setDraft((current) => ({
      ...current,
      rules: current.rules.map((rule) =>
        rule.key === ruleKey
          ? { ...rule, assignments: [...rule.assignments, newAssignmentDraft()] }
          : rule,
      ),
    }))
  }, [])

  const updateAssignment = useCallback(
    (ruleKey: string, key: string, patch: Partial<ActionAssignmentDraft>) => {
      setDraft((current) => ({
        ...current,
        rules: current.rules.map((rule) => {
          if (rule.key !== ruleKey) return rule
          return {
            ...rule,
            assignments: rule.assignments.map((assignment) => {
              if (assignment.key !== key) return assignment
              // 换值来源就换一整套列，别让旧来源的 id 混进新来源里。
              if (patch.valueSource && patch.valueSource !== assignment.valueSource) {
                const cleared = {
                  valueParameterId: '',
                  objectParameterId: '',
                  objectPropertyId: '',
                  staticValue: '',
                }
                return { ...assignment, ...cleared, ...patch }
              }
              return { ...assignment, ...patch }
            }),
          }
        }),
      }))
    },
    [],
  )

  const removeAssignment = useCallback((ruleKey: string, key: string) => {
    setDraft((current) => ({
      ...current,
      rules: current.rules.map((rule) =>
        rule.key === ruleKey
          ? { ...rule, assignments: rule.assignments.filter((item) => item.key !== key) }
          : rule,
      ),
    }))
  }, [])

  const addCondition = useCallback(() => {
    setDraft((current) => ({
      ...current,
      conditions: [...current.conditions, newConditionDraft()],
    }))
  }, [])

  const updateCondition = useCallback((key: string, patch: Partial<ActionConditionDraft>) => {
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.map((condition) => {
        if (condition.key !== key) return condition
        // 判空类运算符没有右侧，切过去就把右侧收掉。
        if (patch.operator && operatorOption(patch.operator)?.unary) {
          return { ...condition, ...patch, right: emptyOperandDraft('static') }
        }
        return { ...condition, ...patch }
      }),
    }))
  }, [])

  const updateOperand = useCallback(
    (key: string, side: ConditionSide, patch: Partial<ActionOperandDraft>) => {
      setDraft((current) => ({
        ...current,
        conditions: current.conditions.map((condition) => {
          if (condition.key !== key) return condition
          const operand = condition[side]
          const next =
            patch.kind && patch.kind !== operand.kind
              ? { ...emptyOperandDraft(patch.kind), ...patch }
              : { ...operand, ...patch }
          return { ...condition, [side]: next }
        }),
      }))
    },
    [],
  )

  const removeCondition = useCallback((key: string) => {
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.filter((condition) => condition.key !== key),
    }))
  }, [])

  const issueFor = useCallback((key: string) => issues.rows[key] ?? null, [issues])

  return {
    draft,
    issueFor,
    firstIssue: issues.first,
    dirty,
    setName,
    setDescription,
    addParameter,
    updateParameter,
    removeParameter,
    addRule,
    updateRule,
    removeRule,
    addAssignment,
    updateAssignment,
    removeAssignment,
    addCondition,
    updateCondition,
    updateOperand,
    removeCondition,
  }
}
