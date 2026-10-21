import uuid

from app.schemas.data_pipeline_execution import BuildRead
from app.services.data_service import pipeline_mapping


def test_build_output_exposes_dataset_and_version_identity():
    output_id = uuid.uuid4()
    dataset_id = uuid.uuid4()
    dataset_version_id = uuid.uuid4()
    row = {
        "id": str(uuid.uuid4()),
        "pipelineId": str(uuid.uuid4()),
        "pipelineVersionId": str(uuid.uuid4()),
        "status": "succeeded",
        "trigger": "manual",
        "outputs": [
            {
                "id": str(uuid.uuid4()),
                "outputId": str(output_id),
                "datasetId": str(dataset_id),
                "datasetVersionId": str(dataset_version_id),
                "outputType": "dataset",
                "status": "succeeded",
                "result": {"expectations": []},
                "stats": {"rowCount": 12},
            }
        ],
    }

    mapped = pipeline_mapping.build(row)
    assert mapped["outputs"][0]["dataset_id"] == str(dataset_id)
    assert mapped["outputs"][0]["dataset_version_id"] == str(dataset_version_id)

    read = BuildRead.model_validate(mapped)
    assert read.outputs[0].dataset_id == dataset_id
    assert read.outputs[0].dataset_version_id == dataset_version_id


def test_non_dataset_output_keeps_identity_null():
    row = {
        "id": str(uuid.uuid4()),
        "pipelineId": str(uuid.uuid4()),
        "status": "queued",
        "trigger": "manual",
        "outputs": [
            {
                "id": str(uuid.uuid4()),
                "outputId": str(uuid.uuid4()),
                "outputType": "virtual",
                "status": "queued",
            }
        ],
    }

    mapped = pipeline_mapping.build(row)
    assert mapped["outputs"][0]["dataset_id"] is None
    assert mapped["outputs"][0]["dataset_version_id"] is None
