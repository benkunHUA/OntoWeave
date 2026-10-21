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
        "logUri": "s3://logs/build.txt",
        "outputs": [
            {
                "id": str(uuid.uuid4()),
                "outputId": str(output_id),
                "datasetId": str(dataset_id),
                "datasetVersionId": str(dataset_version_id),
                "outputType": "dataset",
                "status": "succeeded",
                "logUri": "s3://logs/output.txt",
                "result": {"expectations": []},
                "stats": {"rowCount": 12},
            }
        ],
    }

    mapped = pipeline_mapping.build(row)
    assert mapped["outputs"][0]["dataset_id"] == str(dataset_id)
    assert mapped["outputs"][0]["dataset_version_id"] == str(dataset_version_id)
    assert mapped["log_uri"] == "s3://logs/build.txt"
    assert mapped["outputs"][0]["log_uri"] == "s3://logs/output.txt"

    read = BuildRead.model_validate(mapped)
    assert read.outputs[0].dataset_id == dataset_id
    assert read.outputs[0].dataset_version_id == dataset_version_id
    assert read.log_uri == "s3://logs/build.txt"
    assert read.outputs[0].log_uri == "s3://logs/output.txt"


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


def test_output_source_build_exposes_log_uri():
    mapped = pipeline_mapping.output_sources(
        {
            "pipelineId": str(uuid.uuid4()),
            "datasetId": str(uuid.uuid4()),
            "versionId": str(uuid.uuid4()),
            "output": {
                "outputId": str(uuid.uuid4()),
                "name": "orders_clean",
                "versionNumber": 3,
                "rowCount": 12,
            },
            "build": {
                "buildId": str(uuid.uuid4()),
                "status": "succeeded",
                "trigger": "manual",
                "logUri": "http://flink:8081/#/job/job-1/exceptions",
            },
            "inputs": [],
        }
    )

    assert mapped["build"]["log_uri"] == "http://flink:8081/#/job/job-1/exceptions"
