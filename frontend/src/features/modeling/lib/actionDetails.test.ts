import { describe, expect, it } from 'vitest'
import type { ActionDetail, GraphProperty, GraphSnapshot } from '../../../shared/types'
import {
  draftFromActionDetail,
  newAssignmentDraft,
  newConditionDraft,
  newParameterDraft,
  newRuleDraft,
  type ActionDraft,
} from './actionDraft'
import { buildActionEdits, isActionDraftDirty } from './actionEdits'
import { actionDraftIssues } from './actionValidation'

function property(id: string, name: string, datatype: string): GraphProperty {
  return {
    id,
    name,
    datatype,
    required: false,
    is_array: false,
    is_primary_key: false,
    is_title_key: false,
    enum_values: datatype === 'enum' ? ['新建', '处理中'] : null,
    status: 'confirmed',
  }
}

function snapshot(): GraphSnapshot {
  return {
    graph: { id: 'g1', name: '客服图' },
    nodes: [
      {
        id: 't-ticket',
        name: '工单',
        description: null,
        status: 'confirmed',
        properties: [property('p-status', '状态', 'enum'), property('p-level', '优先级', 'integer')],
      },
      {
        id: 't-staff',
        name: '员工',
        description: null,
        status: 'confirmed',
        properties: [property('p-name', '姓名', 'text')],
      },
    ],
    relations: [
      {
        id: 'r-assign',
        name: '负责',
        source_type_id: 't-ticket',
        target_type_id: 't-staff',
        direction: 'forward',
        cardinality: 'many_to_many',
        forward_label: '负责',
        reverse_label: null,
        foreign_key_property_id: null,
        status: 'confirmed',
      },
    ],
    actions: [],
  }
}

function detail(): ActionDetail {
  return {
    id: 'a1',
    name: '指派工单',
    description: '把工单派给员工',
    status: 'draft',
    parameters: [
      {
        id: 'par-ticket',
        name: '工单',
        description: null,
        kind: 'object_ref',
        datatype: null,
        target_object_type_id: 't-ticket',
        is_array: false,
        is_required: true,
        default_value: null,
        enum_values: null,
        order_index: 0,
        status: 'draft',
      },
      {
        id: 'par-staff',
        name: '员工',
        description: null,
        kind: 'object_ref',
        datatype: null,
        target_object_type_id: 't-staff',
        is_array: false,
        is_required: true,
        default_value: null,
        enum_values: null,
        order_index: 1,
        status: 'draft',
      },
    ],
    rules: [
      {
        id: 'rule-1',
        rule_kind: 'modify_object',
        target_object_type_id: 't-ticket',
        target_relation_id: null,
        source_parameter_id: 'par-ticket',
        target_parameter_id: null,
        order_index: 0,
        status: 'draft',
        assignments: [
          {
            id: 'as-1',
            target_property_id: 'p-level',
            value_source: 'static',
            value_parameter_id: null,
            object_parameter_id: null,
            object_property_id: null,
            static_value: 3,
            status: 'draft',
          },
        ],
      },
    ],
    criteria: [
      {
        id: 'node-root',
        node_kind: 'group',
        logic: 'and',
        operator: null,
        failure_message: null,
        order_index: 0,
        left: null,
        right: null,
        children: [
          {
            id: 'node-1',
            node_kind: 'condition',
            logic: null,
            operator: 'is not null',
            failure_message: '工单必须有优先级',
            order_index: 0,
            left: {
              side: 'left',
              kind: 'object_property',
              value_parameter_id: null,
              object_parameter_id: 'par-ticket',
              object_property_id: 'p-level',
              static_value: null,
            },
            right: null,
            children: [],
          },
        ],
      },
    ],
  }
}

function draftOf(): ActionDraft {
  return draftFromActionDetail(detail())
}

describe('draftFromActionDetail', () => {
  it('reads back a definition with nothing to save', () => {
    const draft = draftOf()
    expect(actionDraftIssues(draft, snapshot()).first).toBeNull()
    expect(isActionDraftDirty(detail(), draft, snapshot())).toBe(false)
    expect(buildActionEdits(detail(), draft, snapshot())).toEqual([])
  })

  it('flattens the criteria tree into editable conditions', () => {
    const draft = draftOf()
    expect(draft.conditions.map((condition) => condition.key)).toEqual(['node-1'])
    expect(draft.conditions[0].left.objectPropertyId).toBe('p-level')
  })
})

describe('buildActionEdits', () => {
  it('only sends the fields that changed', () => {
    const draft = draftOf()
    draft.name = '指派工单给员工'
    draft.rules[0].assignments[0].staticValue = '5'

    expect(buildActionEdits(detail(), draft, snapshot())).toEqual([
      {
        op: 'update',
        element_kind: 'action_type',
        element_id: 'a1',
        patch: { name: '指派工单给员工' },
      },
      {
        op: 'update',
        element_kind: 'action_rule_assignment',
        element_id: 'as-1',
        patch: { static_value: 5 },
      },
    ])
  })

  it('creates a parameter with the whole shape in one edit', () => {
    const draft = draftOf()
    const parameter = { ...newParameterDraft(), name: '说明', datatype: 'text' }
    draft.parameters.push(parameter)

    const edits = buildActionEdits(detail(), draft, snapshot())
    expect(edits).toEqual([
      {
        op: 'add_action_parameter',
        action_type_id: 'a1',
        name: '说明',
        description: null,
        is_required: false,
        kind: 'value',
        datatype: 'text',
        target_object_type_id: null,
        enum_values: null,
        default_value: null,
        is_array: false,
      },
    ])
  })

  it('switches a parameter between value and object by sending both column groups', () => {
    const draft = draftOf()
    const ticket = draft.parameters[0]
    ticket.kind = 'value'
    ticket.datatype = 'text'

    const edits = buildActionEdits(detail(), draft, snapshot())
    expect(edits).toContainEqual({
      op: 'update',
      element_kind: 'action_parameter',
      element_id: 'par-ticket',
      patch: {
        kind: 'value',
        datatype: 'text',
        target_object_type_id: null,
        enum_values: null,
        is_array: false,
        default_value: null,
      },
    })
  })

  it('adds a link rule together with its inline assignments', () => {
    const draft = draftOf()
    const rule = newRuleDraft('create_link')
    rule.targetRelationId = 'r-assign'
    rule.sourceParameterId = 'par-ticket'
    rule.targetParameterId = 'par-staff'
    draft.rules.push(rule)

    expect(buildActionEdits(detail(), draft, snapshot())).toContainEqual({
      op: 'add_action_rule',
      action_type_id: 'a1',
      rule_kind: 'create_link',
      target_relation_id: 'r-assign',
      source_parameter_id: 'par-ticket',
      target_parameter_id: 'par-staff',
    })
  })

  it('adds one assignment to an existing rule instead of rewriting the rule', () => {
    const draft = draftOf()
    const assignment = newAssignmentDraft()
    assignment.targetPropertyId = 'p-status'
    assignment.valueSource = 'static'
    assignment.staticValue = '处理中'
    draft.rules[0].assignments.push(assignment)

    const edits = buildActionEdits(detail(), draft, snapshot())
    expect(edits).toEqual([
      {
        op: 'add_action_rule_assignment',
        rule_id: 'rule-1',
        target_property_id: 'p-status',
        value_source: 'static',
        static_value: '处理中',
      },
    ])
  })

  it('replaces the whole criteria list when a condition changes', () => {
    const draft = draftOf()
    const condition = newConditionDraft()
    condition.operator = 'is'
    condition.left = {
      kind: 'object_property',
      valueParameterId: '',
      objectParameterId: 'par-ticket',
      objectPropertyId: 'p-status',
      staticValue: '',
    }
    condition.right = { ...newConditionDraft().right, kind: 'static', staticValue: '处理中' }
    draft.conditions.push(condition)

    const edits = buildActionEdits(detail(), draft, snapshot())
    expect(edits).toHaveLength(1)
    expect(edits[0].op).toBe('set_action_criteria')
    expect(edits[0]).toMatchObject({
      conditions: [
        { operator: 'is not null', right: null },
        { operator: 'is', right: { kind: 'static', static_value: '处理中' } },
      ],
    })
  })

  it('marks removed parameters and rules for deletion', () => {
    const draft = draftOf()
    draft.parameters = draft.parameters.filter((item) => item.id !== 'par-staff')
    draft.removedParameterIds = ['par-staff']
    draft.rules = []
    draft.removedRuleIds = ['rule-1']

    const edits = buildActionEdits(detail(), draft, snapshot())
    expect(edits).toContainEqual({
      op: 'delete',
      element_kind: 'action_parameter',
      element_id: 'par-staff',
    })
    expect(edits).toContainEqual({ op: 'delete', element_kind: 'action_rule', element_id: 'rule-1' })
  })
})

describe('actionDraftIssues', () => {
  it('flags a rule whose object parameter was deleted from the draft', () => {
    const draft = draftOf()
    draft.parameters = draft.parameters.filter((item) => item.id !== 'par-ticket')
    draft.removedParameterIds = ['par-ticket']

    const issues = actionDraftIssues(draft, snapshot())
    expect(issues.rows['rule-1']).toContain('引用')
    expect(issues.rows['node-1']).toContain('引用')
  })

  it('flags an operator that does not fit the left datatype', () => {
    const draft = draftOf()
    draft.conditions[0].operator = '<'
    draft.conditions[0].right = { ...newConditionDraft().right, kind: 'static', staticValue: '3' }
    // 优先级是整数：比大小放行。
    expect(actionDraftIssues(draft, snapshot()).rows['node-1']).toBeUndefined()

    // 换成文本属性之后比大小就说不通了。
    draft.conditions[0].left.objectParameterId = 'par-staff'
    draft.conditions[0].left.objectPropertyId = 'p-name'
    expect(actionDraftIssues(draft, snapshot()).rows['node-1']).toContain('不适用')
  })

  it('flags an assignment whose source datatype does not match the property', () => {
    const draft = draftOf()
    draft.rules[0].assignments[0] = {
      ...newAssignmentDraft(),
      key: 'as-2',
      targetPropertyId: 'p-level',
      valueSource: 'static',
      staticValue: '很高',
    }
    expect(actionDraftIssues(draft, snapshot()).rows['as-2']).toContain('整数')
  })

  it('flags assignments on a rule kind that takes none', () => {
    const draft = draftOf()
    draft.rules[0].ruleKind = 'delete_object'
    expect(actionDraftIssues(draft, snapshot()).rows['rule-1']).toContain('不带字段赋值')
  })

  it('flags a nameless parameter and a duplicate name', () => {
    const draft = draftOf()
    draft.parameters.push({ ...newParameterDraft(), name: '员工' })
    const issues = actionDraftIssues(draft, snapshot())
    expect(issues.rows[draft.parameters[2].key]).toContain('重复')
  })

  it('reports the first problem so the drawer can disable saving', () => {
    const draft = draftOf()
    draft.name = '  '
    expect(actionDraftIssues(draft, snapshot())).toEqual({
      rows: { name: '动作名不能为空' },
      first: '动作名不能为空',
    })
  })
})
