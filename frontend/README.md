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
  app/                        应用入口与路由
  features/
    home/
      views/                  首页页面
    modeling/
      views/                  建模页面
      components/             画布、节点等组件
      lib/                    画布布局与数据转换
  shared/
    api/                      REST / SSE 客户端
    types/                    共享类型
  styles/                     全局与应用样式
```

工作台通过 REST 读取会话、图快照、改动集和视图状态，通过 SSE 接收智能体回合；
对话进入时读取 `/conversations/{id}/messages` 恢复持久化的消息历史。
会话标题可以直接编辑，保存时会同步更新左侧列表、中间标题、图名称和视图名称。
节点拖拽坐标会 debounce 后写回 `view_state`，后续 `state.update` 会带回子智能体
产出的声明式视图规格。
