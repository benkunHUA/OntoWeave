"""改动集的确认与撤销。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

import app.services.changeset.common as common
import app.services.changeset.ops as ops
import app.services.changeset.store as store
from app.errors import InvalidTransitionError, InvariantViolationError
from app.models import Changeset, ChangesetOp
from app.services import ontology, view_state
from app.services.element_kinds.registry import REGISTRY


def promote(
    session: Session,
    kind: str,
    element_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    entry = REGISTRY.get(kind)
    if entry is None:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")
    entry.promote(session, element_id, owner_id=owner_id)


def delete_elements(
    session: Session,
    elements: list[tuple[str, uuid.UUID]],
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """按「先子后父」删除一批元素，顺序稳定、不重复删被级联带走的行。

    子元素对父元素是 ON DELETE CASCADE——先删父行的话数据库会把子行一起带走，
    ORM 再删一次就是 0 匹配（SAWarning）。删除顺序由注册表的 delete_phase 决定，
    每阶段 flush 一次，把 DELETE 顺序钉死；同一档位里若有自引用（条件节点），
    再由登记项的 order_for_delete 给出子先父后的顺序。
    """
    phases = {kind: REGISTRY[kind].delete_phase for kind, _ in elements}
    for kind in sorted(phases, key=lambda item: phases[item]):
        entry = REGISTRY[kind]
        pending = [
            common.load_element(session, element_kind, element_id, owner_id=owner_id)
            for element_kind, element_id in elements
            if element_kind == kind
        ]
        # 同一档位里还有自引用时（条件节点的 parent_id），登记项会给出子先父后的顺序。
        # 这种表必须一条一条删：一批删会被 ORM 打包成 executemany，落库顺序就不再是
        # 我们给的顺序，父行先落地会把子行级联带走。
        ordered = entry.order_for_delete is not None
        if entry.order_for_delete is not None:
            pending = list(entry.order_for_delete(session, pending))
        for element in pending:
            session.delete(element)
            if ordered:
                session.flush()
        if pending and not ordered:
            session.flush()


def restore_view(
    session: Session,
    cs: Changeset,
    op: ChangesetOp,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把视图状态翻回这条视图改动发生前快照的样子。"""
    ops.validate_view_op(op)
    view_state.upsert_view_state(
        session,
        graph_id=cs.graph_id,
        user_id=uuid.UUID(str(op.payload["user_id"])),
        spec=dict(op.payload["before"]),
        owner_id=owner_id,
    )


def confirm_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """确认：新建的元素翻成已确认，标记删除的元素真正删掉。

    先把每条操作校验干净——种类合法、目标存在、状态允许流转——再落动作，
    这样动作阶段不会因为合法性问题中途失败。
    """
    cs = store.lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != common.OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    changeset_ops = store.list_ops(session, cs.id)
    for op in changeset_ops:
        if op.op == common.OP_VIEW:
            # 视图改动在记录时就已经生效，确认阶段没有本体动作要做。
            continue
        ops.validate_op(op.op, op.target_id, op.payload)
        kind = common.op_kind(op)
        target_id = common.op_target(op)
        element = common.load_element(session, kind, target_id, owner_id=owner_id)
        if op.op == common.OP_CREATE and element.status != ontology.DRAFT:
            raise InvalidTransitionError(
                f"{kind} {target_id} is {element.status}, cannot be confirmed from here"
            )

    deletions: list[tuple[str, uuid.UUID]] = []
    for op in changeset_ops:
        if op.op == common.OP_VIEW:
            continue
        if op.op == common.OP_CREATE:
            promote(session, common.op_kind(op), common.op_target(op), owner_id=owner_id)
        elif op.op == common.OP_UPDATE:
            kind = common.op_kind(op)
            element = common.load_element(session, kind, common.op_target(op), owner_id=owner_id)
            common.apply_fields(element, op.payload["after"])
        elif op.op == common.OP_DELETE:
            deletions.append((common.op_kind(op), common.op_target(op)))

    delete_elements(session, deletions, owner_id=owner_id)
    cs.status = common.CONFIRMED
    session.flush()
    return cs


def reject_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """撤销：删掉本次新建的元素、把改过的还原；标记删除的本来就没删，无事发生。"""
    cs = store.lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != common.OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    changeset_ops = store.list_ops(session, cs.id)
    for op in changeset_ops:
        if op.op == common.OP_VIEW:
            ops.validate_view_op(op)
            continue
        ops.validate_op(op.op, op.target_id, op.payload)
        common.load_element(session, common.op_kind(op), common.op_target(op), owner_id=owner_id)

    created: list[tuple[str, uuid.UUID]] = []
    for op in reversed(changeset_ops):
        if op.op == common.OP_VIEW:
            restore_view(session, cs, op, owner_id=owner_id)
        elif op.op == common.OP_CREATE:
            created.append((common.op_kind(op), common.op_target(op)))
        elif op.op == common.OP_UPDATE:
            element = common.load_element(
                session, common.op_kind(op), common.op_target(op), owner_id=owner_id
            )
            common.apply_fields(element, op.payload.get("before", {}))
        elif op.op == common.OP_DELETE:
            pass  # 草稿阶段没真删

    delete_elements(session, created, owner_id=owner_id)
    cs.status = common.REJECTED
    session.flush()
    return cs
