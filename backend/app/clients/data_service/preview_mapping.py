"""数据集预览 Java DTO 的共享字段映射。"""

from __future__ import annotations


def dataset_preview(row: dict) -> dict:
    return {
        "dataset_id": row.get("datasetId"),
        "version_id": row.get("versionId"),
        "version_number": row.get("versionNumber"),
        "snapshot_id": row.get("snapshotId"),
        "live": row.get("live", False),
        "columns": row.get("columns") or [],
        "row_count": row.get("rowCount", 0),
        "rows": row.get("rows") or [],
        "truncated": row.get("truncated", False),
    }
