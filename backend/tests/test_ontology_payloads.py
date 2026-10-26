from __future__ import annotations

import uuid

from app.api.ontology.payloads import runs


def test_runs_normalizes_null_stats_to_empty_dict() -> None:
    row = {
        "id": str(uuid.uuid4()),
        "owner_id": str(uuid.uuid4()),
        "subject_kind": "RELATION",
        "subject_id": str(uuid.uuid4()),
        "binding_id": None,
        "trigger_type": "MANUAL",
        "status": "QUEUED",
        "phase": None,
        "target_dataset_kind": "SYNC",
        "target_dataset_version_id": str(uuid.uuid4()),
        "mapping_revision": 1,
        "requested_at": "2026-10-24T17:20:45Z",
        "stats": None,
    }

    assert runs([row])[0].stats == {}
