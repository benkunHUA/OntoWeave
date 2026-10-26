import { describe, expect, it } from 'vitest'
import type { GraphNode } from '../../../shared/types'
import {
  ENUM_DATATYPE,
  buildElementEdits,
  datatypeOptions,
  draftFromNode,
  draftValidationError,
  isDraftDirty,
  newPropertyDraft,
} from './nodeDetails'

function node(overrides: Partial<GraphNode> = {}): GraphNode {
  return {
    id: 'obj1',
    name: '客户',
    description: '购买商品的人',
    status: 'confirmed',
    properties: [
      {
        id: 'p1',
        name: '客户名',
        datatype: 'text',
        required: true,
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
        enum_values: null,
        status: 'confirmed',
      },
    ],
    ...overrides,
  }
}

describe('buildElementEdits', () => {
  it('emits nothing when the draft matches the node', () => {
    const source = node()
    expect(buildElementEdits(source, draftFromNode(source))).toEqual([])
  })

  it('sends only the changed object fields', () => {
    const source = node()
    const draft = { ...draftFromNode(source), name: '终端客户' }

    expect(buildElementEdits(source, draft)).toEqual([
      { op: 'update', element_kind: 'object_type', element_id: 'obj1', patch: { name: '终端客户' } },
    ])
  })

  it('sends a changed object english name', () => {
    const source = node({ name_en: 'customer' })
    const draft = { ...draftFromNode(source), nameEn: 'client' }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'object_type',
        element_id: 'obj1',
        patch: { name_en: 'client' },
      },
    ])
  })

  it('normalises an emptied description to null', () => {
    const source = node()
    const draft = { ...draftFromNode(source), description: '   ' }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'object_type',
        element_id: 'obj1',
        patch: { description: null },
      },
    ])
  })

  it('emits a property update for a changed field name or type', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties[0] = { ...draft.properties[0], name: '客户全称', datatype: 'decimal' }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { name: '客户全称', datatype: 'decimal' },
      },
    ])
  })

  it('emits a create_property op for a new field and trims its name', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties.push({ ...newPropertyDraft(), name: '  等级  ', datatype: 'integer' })

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'create_property',
        owner_type_id: 'obj1',
        name: '等级',
        datatype: 'integer',
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
      },
    ])
  })

  it('carries english names on property creates and updates', () => {
    const source = node({
      properties: [
        {
          id: 'p1',
          name: '客户名',
          name_en: 'customer_name',
          datatype: 'text',
          required: true,
          is_array: false,
          is_primary_key: false,
          is_title_key: false,
          enum_values: null,
          status: 'confirmed',
        },
      ],
    })
    const draft = draftFromNode(source)
    draft.properties[0] = { ...draft.properties[0], nameEn: 'customer_full_name' }
    draft.properties.push({ ...newPropertyDraft(), name: '等级', nameEn: 'level' })

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { name_en: 'customer_full_name' },
      },
      {
        op: 'create_property',
        owner_type_id: 'obj1',
        name: '等级',
        name_en: 'level',
        datatype: 'text',
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
      },
    ])
  })

  it('carries key and array flags on property updates', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties[0] = {
      ...draft.properties[0],
      isArray: true,
      isPrimaryKey: true,
      isTitleKey: true,
    }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { is_array: true, is_primary_key: true, is_title_key: true },
      },
    ])
  })

  it('emits a delete op for a removed existing field', () => {
    const source = node()
    const draft = { ...draftFromNode(source), properties: [], removedIds: ['p1'] }

    expect(buildElementEdits(source, draft)).toEqual([
      { op: 'delete', element_kind: 'property', element_id: 'p1' },
    ])
  })

  it('trims the object name before comparing', () => {
    const source = node()
    const draft = { ...draftFromNode(source), name: '  客户  ' }
    expect(buildElementEdits(source, draft)).toEqual([])
    expect(isDraftDirty(source, draft)).toBe(false)
  })
})

describe('draftValidationError', () => {
  it('rejects an empty object name or field name', () => {
    const source = node()
    expect(draftValidationError({ ...draftFromNode(source), name: '  ' })).toBe(
      '对象名称不能为空',
    )
    const draft = draftFromNode(source)
    draft.properties[0] = { ...draft.properties[0], name: '' }
    expect(draftValidationError(draft)).toBe('字段名不能为空')
  })

  it('blocks a brand new field that has no name yet', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties.push(newPropertyDraft())
    expect(draftValidationError(draft)).toBe('字段名不能为空')
  })

  it('passes a complete draft', () => {
    expect(draftValidationError(draftFromNode(node()))).toBeNull()
  })
})

describe('datatypeOptions', () => {
  it('keeps an unknown legacy datatype selectable', () => {
    const options = datatypeOptions('custom')
    expect(options.at(-1)).toEqual({ value: 'custom', label: 'custom' })
  })

  it('does not duplicate a known datatype', () => {
    expect(datatypeOptions('text')).not.toContainEqual({ value: 'text', label: 'text' })
  })
})

describe('draftFromNode', () => {
  it('drops properties that are pending deletion', () => {
    const draft = draftFromNode(node(), new Set(['p1']))
    expect(draft.properties).toEqual([])
  })
})

describe('enum 候选值', () => {
  function enumNode() {
    return node({
      properties: [
        {
          id: 'p1',
          name: '状态',
          datatype: ENUM_DATATYPE,
          required: false,
          is_array: false,
          is_primary_key: false,
          is_title_key: false,
          enum_values: ['待处理', '已完成'],
          status: 'confirmed',
        },
      ],
    })
  }

  it('draft 里带上候选值，原样不动时不产出编辑', () => {
    const source = enumNode()
    expect(draftFromNode(source).properties[0].enumValues).toEqual(['待处理', '已完成'])
    expect(buildElementEdits(source, draftFromNode(source))).toEqual([])
  })

  it('改候选值时连带去掉首尾空白', () => {
    const source = enumNode()
    const draft = draftFromNode(source)
    draft.properties[0].enumValues = [' 待处理 ', '已完成', '已关闭']

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { enum_values: ['待处理', '已完成', '已关闭'] },
      },
    ])
  })

  it('从枚举换成别的类型时把候选值一起清空', () => {
    const source = enumNode()
    const draft = draftFromNode(source)
    draft.properties[0].datatype = 'text'

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { datatype: 'text', enum_values: null },
      },
    ])
  })

  it('新增枚举字段时把候选值发给后端', () => {
    const source = node()
    const draft = draftFromNode(source)
    const added = newPropertyDraft()
    added.name = '状态'
    added.datatype = ENUM_DATATYPE
    added.enumValues = ['待处理', '已完成']
    draft.properties = [...draft.properties, added]

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'create_property',
        owner_type_id: 'obj1',
        name: '状态',
        datatype: ENUM_DATATYPE,
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
        enum_values: ['待处理', '已完成'],
      },
    ])
  })

  it('候选值不足或重复时草稿不合法', () => {
    const source = enumNode()
    const draft = draftFromNode(source)
    draft.properties[0].enumValues = ['待处理']
    expect(draftValidationError(draft)).toContain('至少要有 2 个互不相同的取值')

    draft.properties[0].enumValues = ['待处理', '待处理']
    expect(draftValidationError(draft)).toContain('至少要有 2 个互不相同的取值')

    draft.properties[0].enumValues = ['待处理', '已完成']
    expect(draftValidationError(draft)).toBeNull()
  })
})
