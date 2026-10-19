# 阶段文件说明

本目录每个阶段一个文件，用于回答三个问题：目标是什么、现在做到哪、详细讨论在哪里。

总入口：[../ROADMAP.md](../ROADMAP.md)。

- 阶段文件不复制设计正文，只做索引和状态维护。
- 设计结论写进 `../specs/`，实施步骤与验收写进 `../plans/`。
- 每次讨论形成结论后，在阶段文件追加一行「沟通记录」。
- 阶段状态以 `../ROADMAP.md` 为准，阶段文件只记录本阶段的细节。

## 文件

| 阶段 | 文件 |
|---|---|
| P1 数据进来 | [p1-data-ingestion.md](p1-data-ingestion.md) |
| P2 数据整理 | [p2-data-preparation.md](p2-data-preparation.md) |
| P3 变成对象 | [p3-ontology-mapping.md](p3-ontology-mapping.md) |
| P4 Agent 使用 | [p4-agent-loop.md](p4-agent-loop.md) |

## 模板

```markdown
# PX 阶段名称

- 状态：
- 目标：
- 完成标准：

## 任务拆分

## 已确认约束

## 沟通记录

## 文档索引

## 遗留问题

## 下一步
```
