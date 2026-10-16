"""把编辑操作登记进改动集。

调用方有两种——界面上的直接编辑，以及模型通过工具写入的草稿——两者落在同一套规则上：
先校验归属与当前状态，再改本体，最后登记成对应的操作（update / create / delete）。
登记失败就直接抛错，半成品由调用方的单元事务整体回滚——这一层不自己提交或回滚。
接口层与工具层只做参数转换。

分派只有两处：写入（新建 / 整棵替换）查 `_WRITE_HANDLERS` 表，更新与删除查
`element_kinds` 注册表。新增一种元素 = 加一个 op dataclass + 一个实现 + 各登一行。
"""

from __future__ import annotations

import dataclasses
import uuid
from collections.abc import Callable

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Changeset, ChangesetOp
from app.services import action_references, changeset, element_kinds, embedding
from app.services.element_edit import creates
from app.services.element_edit.criteria import replace_action_criteria
from app.services.element_edit.ops import (
    ACTION_TYPE,
    OBJECT_TYPE,
    ActionCriteriaReplace,
    ActionParameterCreate,
    ActionRuleAssignmentCreate,
    ActionRuleCreate,
    ActionTypeCreate,
    EditOp,
    ElementDelete,
    ElementUpdate,
    ObjectTypeCreate,
    ObjectTypeReuse,
    PropertyCreate,
    RelationCreate,
)

# 改这些字段会改变对象的检索语义（名称 + 描述拼成检索文本），要重算向量。
_EMBEDDING_FIELDS = {"name", "description"}

_WriteHandler = Callable[..., object]

#: 写入类操作（新建、整棵替换）→ 实现。op 的字段直接当关键词参数传给实现，
#: 所以两边字段名必须一致；通用的 update / delete 不在这张表里，它们各有一条固定路径。
_WRITE_HANDLERS: dict[type, _WriteHandler] = {
    PropertyCreate: creates.create_property,
    ObjectTypeCreate: creates.create_object_type,
    ObjectTypeReuse: creates.reuse_object_type,
    RelationCreate: creates.create_relation,
    ActionTypeCreate: creates.create_action_type,
    ActionParameterCreate: creates.create_action_parameter,
    ActionRuleCreate: creates.create_action_rule,
    ActionRuleAssignmentCreate: creates.create_action_rule_assignment,
    ActionCriteriaReplace: replace_action_criteria,
}


def write_handlers() -> dict[type, _WriteHandler]:
    """写入类操作当前登记了哪些实现。

    给「漏登记」兜底：新增 op 却忘了配实现，测试直接炸，而不是等用户保存时 500。
    """
    return dict(_WRITE_HANDLERS)


def apply_edits(
    session: Session,
    *,
    graph_id: uuid.UUID,
    ops: list[EditOp],
    owner_id: uuid.UUID | None = None,
    embedder: embedding.Embedder | None = None,
) -> Changeset:
    """把一批编辑登记进图上未定稿的改动集，返回那个改动集。

    覆盖本体改动的全部形态：建对象类型、复用已有类型、建关系、加字段、改字段、标记删除。
    写入一律落在某个 owner 名下，缺 owner 直接拒绝，避免建出无归属的本体元素。
    逐条处理：服务层自己采集 before / 校验当前值 / 应用 after，所以整批编辑要么全成，
    要么随事务一起回滚。
    """
    if not ops:
        raise InvariantViolationError("at least one edit is required")
    if owner_id is None:
        raise InvariantViolationError("owner is required to register edits")
    cs = changeset.ensure_open_changeset(session, graph_id=graph_id, owner_id=owner_id)
    for op in ops:
        if isinstance(op, ElementUpdate):
            record_update(
                session,
                changeset_id=cs.id,
                element_kind=op.element_kind,
                element_id=op.element_id,
                patch=op.patch,
                owner_id=owner_id,
                embedder=embedder,
            )
        elif isinstance(op, ElementDelete):
            remove_element(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                element_kind=op.element_kind,
                element_id=op.element_id,
            )
        else:
            _apply_write_op(session, op, changeset_id=cs.id, owner_id=owner_id)
    return cs


def _apply_write_op(
    session: Session,
    op: EditOp,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
) -> None:
    handler = _WRITE_HANDLERS.get(type(op))
    if handler is None:
        raise InvariantViolationError(f"unsupported edit op: {op!r}")
    handler(session, changeset_id=changeset_id, owner_id=owner_id, **dataclasses.asdict(op))


def record_update(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    owner_id: uuid.UUID | None = None,
    embedder: embedding.Embedder | None = None,
) -> ChangesetOp:
    """登记一次字段更新，并在名称/描述变化时刷新对象的检索向量。"""
    # 先做存在性与归属校验，再做语义校验——顺序反了会把别人家的元素也拿来判合法性。
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    _validate_patch(
        session,
        element_kind=element_kind,
        element_id=element_id,
        patch=patch,
        changeset_id=changeset_id,
    )
    op = changeset.record_update(
        session,
        changeset_id=changeset_id,
        element_kind=element_kind,
        element_id=element_id,
        patch=patch,
        owner_id=owner_id,
    )
    if embedder is not None and _EMBEDDING_FIELDS & set(patch):
        if element_kind == OBJECT_TYPE:
            embedding.try_refresh_object_type_embedding(
                session, object_type_id=element_id, embedder=embedder
            )
        elif element_kind == ACTION_TYPE:
            embedding.try_refresh_action_type_embedding(
                session, action_type_id=element_id, embedder=embedder
            )
    return op


def _validate_patch(
    session: Session,
    *,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    changeset_id: uuid.UUID,
) -> None:
    """更新前的语义校验走元素种类注册表，规则在各自领域层，别漏在接口后面。

    顺手把「本改动集里已标删、还没落地的同种元素」交给校验：草稿阶段的删除是延后的，
    库里那些行还在，但它们不该再挡住新值。
    """
    entry = element_kinds.REGISTRY.get(element_kind)
    if entry is not None and entry.validate_patch is not None:
        pending = changeset.pending_delete_ids(session, changeset_id, element_kind)
        entry.validate_patch(session, element_id, patch, pending)


def remove_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把元素标记删除：草稿阶段不真删，用户确认时才删。

    被动作定义引用着的共享元素在这里就拒绝，别等到确认时数据库抛一句 IntegrityError。
    删动作类型这类元素时，连它的子元素也各记一条——数据库的级联带不起来它们（见
    `element_kinds` 的 dependents），实际顺序留到确认时按 delete_phase 排。
    """
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    action_references.ensure_deletable(session, element_kind=element_kind, element_id=element_id)
    entry = element_kinds.REGISTRY.get(element_kind)
    targets = [(element_kind, element_id)]
    if entry is not None and entry.dependents is not None:
        targets.extend(entry.dependents(session, element_id))
    for kind, target_id in targets:
        changeset.record_op(
            session,
            changeset_id=changeset_id,
            op=changeset.OP_DELETE,
            target_id=target_id,
            payload={"kind": kind},
        )
