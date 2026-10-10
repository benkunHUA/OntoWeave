# OntoWeave

会话驱动的企业本体建模工具。业务专家用自然语言讲业务，系统把「人话」抽象成
「对象 / 属性 / 关系」的类型图；团队在这张图上沉淀，最终形成可被智能体消费的企业本体。
思想来源是 Palantir 的 Ontology。

## 当前进度

后端地基层、会话 / 改动集 / 视图、智能体工具与运行时已经完成；前端建模工作台
已接通 REST、SSE、React Flow 和视图状态持久化。

| 阶段 | 状态 |
|---|---|
| 后端地基 | ✅ 已完成 |
| 后端加固（P0–P2） | ✅ 已完成 |
| 会话、改动集与视图 | ✅ 已完成 |
| 工具层与相似检索 | ✅ 已完成 |
| 智能体运行时与 SSE | ✅ 已完成 |
| 前端与视图同步 | ✅ MVP 已完成 |

## 仓库结构

```
backend/                 FastAPI 后端（见 backend/README.md）
frontend/                React + TypeScript 建模工作台
.github/workflows/       CI
```

## 快速开始

整套用容器跑：

```bash
cd backend
docker compose up -d --build
curl http://127.0.0.1:18000/health
```

接口文档：http://127.0.0.1:18000/docs

前端本地开发：

```bash
cd frontend
npm install
npm run dev
```

打开 http://127.0.0.1:5173。Vite 会把 `/api` 代理到本机 `18000` 端口。

本地开发、跑测试、静态检查的详细命令见 [backend/README.md](backend/README.md)。

## 几个关键约定

- **对象与关系在共享池里，不归任何一张图私有**；图通过 `graph_object` / `graph_relation`
  引用它们，跨图复用对象就把多张子图连成一张大图。
- **草稿与正式靠 `status` 区分**（草案 → 已确认 → 已批准），不靠独立的草稿表。
- **事务边界在依赖层**，不在路由里；请求成功才提交，抛异常就回滚。
- **测试库必须以 `_test` 结尾**，否则 pytest 拒绝启动。
- **数据库只监听本机**，应用容器映射到宿主机 18000（8000 常被别的项目占用）。
- **确认和撤销走 REST，智能体回合走 SSE**；视图状态的坐标更新由前端 debounce 后写回。
