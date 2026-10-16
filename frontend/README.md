# OntoWeave Frontend

React + TypeScript + React Flow 建模工作台。

```bash
npm install
npm run dev
```

默认地址为 http://127.0.0.1:5173，`/api` 会代理到本机的 FastAPI 服务
http://127.0.0.1:18000。

页面：

- `/`：功能菜单与最近对话。
- `/modeling/:conversationId`：三栏建模工作台。

目录按功能划分：

```text
src/
  app/                        应用入口与路由（挂 TanStack Query Provider）
  features/
    home/
      views/                  首页页面
    modeling/
      views/                  建模页面（只做编排）
      components/             画布、节点、抽屉（动作相关在 components/action/）
      hooks/                  取数、状态与交互（会话 / 工作台 / 画布选中 / 抽屉）
      context/                Context 装配，按关注点分组
      lib/                    纯函数与配置：布局、草稿、校验、改动集编辑
  shared/
    api/                      按域拆分的 REST / SSE 客户端
    query/                    TanStack Query 客户端
    types/                    按域拆分的共享类型
  styles/                     全局与应用样式
```

工作台通过 REST 读取会话、图快照、改动集和视图状态，通过 SSE 接收智能体回合；
对话进入时读取 `/conversations/{id}/messages` 恢复持久化的消息历史。
会话标题可以直接编辑，保存时会同步更新左侧列表、中间标题、图名称和视图名称。
节点拖拽坐标会 debounce 后写回 `view_state`，后续 `state.update` 会带回子智能体
产出的声明式视图规格。

**动作**在画布上是一条独立的「动作条」（顶部一条紫线，与对象卡片区分），横向排在它
涉及的对象下方居中；`involves` 边自动生成、不能手动连或删，从动作条左 / 右端口各自出去，
不挤在同一个口上，视觉上弱化成细虚线。点动作节点开抽屉，四块：基本信息 / 参数 / 规则 / 提交校验；
新增参数与规则各发一条 create edit，提交校验整棵替换，改名与删除复用通用的 update / delete。
规则引用了已被删除的参数时行内标红并禁用保存。**新建动作只走对话**，界面不提供新建按钮。
