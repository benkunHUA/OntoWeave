# OntoWeave 开发约定

## 代码组织（重点，务必遵守）

- **先规划再落笔**：动手前先想清模块边界与数据流，再决定代码写在哪；不要边写边堆。
- **大文件必须拆**：单文件超过 ~300 行就按职责拆；新增能力优先扩展已有分层，不要就近插入。
- **重复即抽象**：同一结构出现第二次就抽成共享组件/常量/工具，不要复制粘贴。
- **配置与文案外置**：枚举、选项、文案、尺寸等不要写死在逻辑或 JSX 里。

## 后端（backend/，FastAPI）

- `app/api/*` 只做 HTTP 边界：校验入参、调 service、转 schema，不放业务规则。
- `app/services/*` 按领域拆模块，一个模块一个关注点。例：`graph_cleanup` 只负责「删图后共享池怎么清理」这一条策略，会话服务只管会话本身。
- `app/schemas/*` 放请求/响应模型；跨层共享的类型别名也放这里。
- 测试与被测模块对齐：`tests/test_<module>.py`。策略类逻辑单独成文件（如 `tests/test_graph_cleanup.py`），不要在别的测试里顺手塞。

## 前端（frontend/，React + TS）

- `features/<feature>/views/*` 只做编排：取数、状态、组合子组件，不写大段内联 JSX。
- 复用 UI 放 `features/<feature>/components/`；有共同骨架的弹窗收进 `components/dialogs/`（共享外壳：`DialogShell`）。
- 枚举/选项/文案/尺寸放 `features/<feature>/lib/`，组件只负责渲染。
- 跨页面共享的类型放 `shared/types`，API 调用放 `shared/api/client.ts`。
- 样式按区域拆文件（`styles/app.css`、`styles/dialog.css`…），在 `app/App.tsx` 里按顺序 import。

## 校验命令

- 后端：`cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app`
- 前端：`cd frontend && npx tsc -b && npm run lint && npm test && npm run build`
