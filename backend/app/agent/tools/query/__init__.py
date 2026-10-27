"""业务图只读查询工具与子 Agent。

包入口保持为空，避免导入 ``context`` 时顺带加载 ``subagent``，在
``agent.definitions -> tools.query -> runtime -> definitions.registry`` 之间形成环。
"""
