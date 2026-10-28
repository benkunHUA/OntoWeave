import {
  Boxes,
  Database,
  MessageSquareText,
  Network,
  Workflow,
  type LucideIcon,
} from 'lucide-react'

export const HOME_COPY = {
  brand: 'OntoWeave',
  subtitle: '企业本体建模台',
  eyebrow: 'Workspace',
  title: '把业务语言织成企业本体',
  intro: '从一段对话开始，抽取对象、属性和关系。每条改动都先进入草图，再交给团队确认。',
  recentTitle: '最近对话',
  recentEmpty: '还没有会话',
  create: '新建',
  enter: '进入',
} as const

export interface HomeFeature {
  id: string
  title: string
  description: string
  path: string
  icon: LucideIcon
}

export const HOME_FEATURES: readonly HomeFeature[] = [
  {
    id: 'modeling',
    title: '建模',
    description: '新建对话，开始梳理业务结构',
    path: '/modeling',
    icon: Network,
  },
  {
    id: 'query',
    title: '业务问答',
    description: '用业务语言查询已发布业务图，回答附带对象与步骤证据',
    path: '/query',
    icon: MessageSquareText,
  },
  {
    id: 'connections',
    title: '数据连接',
    description: '接入 MySQL，选择数据表并持续同步到数据集',
    path: '/data-connections',
    icon: Database,
  },
  {
    id: 'pipelines',
    title: '数据整理',
    description: '把已同步的数据集组合成可重复执行的清洗流程',
    path: '/pipelines',
    icon: Workflow,
  },
  {
    id: 'ontology',
    title: '对象数据',
    description: '把已发布数据集持续映射成可查询的对象与关系',
    path: '/ontology',
    icon: Boxes,
  },
] as const
