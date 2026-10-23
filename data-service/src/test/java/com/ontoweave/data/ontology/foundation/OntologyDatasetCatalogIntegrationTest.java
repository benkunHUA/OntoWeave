package com.ontoweave.data.ontology.foundation;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.dataset.DatasetPage;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.dataset.DatasetUnavailableException;
import com.ontoweave.data.ontology.dataset.OntologyDatasetCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OntologyDatasetCatalogIntegrationTest extends OntologyFoundationIntegrationTestSupport {

    @Autowired
    private OntologyDatasetCatalog catalog;

    @Test
    void resolvesCurrentPublishedVersionsForSyncAndPipelineDatasets() {
        UUID ownerId = UUID.randomUUID();
        UUID syncDatasetId = insertSyncDataset(ownerId, "customers");
        UUID pipelineDatasetId = insertPipelineDataset(ownerId, "orders");

        DatasetPage page = catalog.listBindable(ownerId, null, 1, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(DatasetRef::kind)
                .containsExactlyInAnyOrder(DatasetKind.SYNC, DatasetKind.PIPELINE);

        DatasetRef sync = catalog.requireCurrent(DatasetKind.SYNC, syncDatasetId, ownerId);
        assertThat(sync.catalog()).isEqualTo("ontoweave");
        assertThat(sync.namespace()).isEqualTo("sync_ns");
        assertThat(sync.table()).isEqualTo("customers");
        assertThat(sync.snapshotId()).isEqualTo(11L);
        assertThat(sync.rowCount()).isEqualTo(2L);
        assertThat(catalog.listColumns(DatasetKind.SYNC, syncDatasetId, ownerId))
                .extracting(column -> column.name())
                .containsExactly("id", "name");

        DatasetRef pipeline = catalog.requireCurrent(
                DatasetKind.PIPELINE, pipelineDatasetId, ownerId
        );
        assertThat(pipeline.catalog()).isEqualTo("ontoweave");
        assertThat(pipeline.namespace()).isEqualTo("pipeline_outputs");
        assertThat(pipeline.table()).isEqualTo("orders");
        assertThat(pipeline.snapshotId()).isEqualTo(21L);
        assertThat(pipeline.rowCount()).isEqualTo(3L);
    }

    @Test
    void hidesDatasetsWithoutPublishedCurrentVersionsAndOtherOwners() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID pipelineDatasetId = insertPipelineDataset(ownerId, "unpublished");
        jdbc.update(
                "UPDATE data.pipeline_dataset_version SET published_at = NULL WHERE dataset_id = ?",
                pipelineDatasetId
        );

        assertThat(catalog.listBindable(ownerId, null, 1, 20).items()).isEmpty();
        assertThat(catalog.resolve(DatasetKind.PIPELINE, pipelineDatasetId)).isEmpty();
        assertThatThrownBy(() -> catalog.requireCurrent(
                DatasetKind.PIPELINE, pipelineDatasetId, ownerId
        ))
                .isInstanceOf(DatasetUnavailableException.class);

        UUID visibleDatasetId = insertSyncDataset(otherOwner, "visible-only-to-other");
        assertThat(catalog.listBindable(ownerId, null, 1, 20).items()).isEmpty();
        assertThatThrownBy(() -> catalog.requireCurrent(
                DatasetKind.SYNC, visibleDatasetId, ownerId
        )).isInstanceOf(DatasetUnavailableException.class);
    }

    private UUID insertSyncDataset(UUID ownerId, String name) {
        UUID connectionId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.data_connection
                    (id, owner_id, name, kind, config, conversation_id)
                VALUES (?, ?, ?, 'mysql', '{}'::jsonb, ?)
                """, connectionId, ownerId, "connection-" + name, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name)
                VALUES (?, ?, ?)
                """, datasetId, connectionId, name);
        jdbc.update("""
                INSERT INTO data.sync_task
                    (id, connection_id, dataset_id, schema_name, table_name, sync_mode,
                     primary_key_columns)
                VALUES (?, ?, ?, 'source', ?, 'snapshot', CAST('["id"]' AS jsonb))
                """, taskId, connectionId, datasetId, name);
        jdbc.update("UPDATE data.dataset SET sync_task_id = ? WHERE id = ?", taskId, datasetId);
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, 1, 'ontoweave', 'sync_ns', ?, 11, 2, false, 128,
                        CAST(? AS jsonb), 2, now(), now(), 'snapshot_end')
                """, versionId, datasetId, name, schema());
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return datasetId;
    }

    private UUID insertPipelineDataset(UUID ownerId, String name) {
        UUID pipelineId = UUID.randomUUID();
        UUID pipelineVersionId = UUID.randomUUID();
        UUID buildId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        UUID buildOutputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline (id, owner_id, conversation_id, name)
                VALUES (?, ?, ?, ?)
                """, pipelineId, ownerId, UUID.randomUUID(), "pipeline-" + name);
        jdbc.update("""
                INSERT INTO data.pipeline_version
                    (id, pipeline_id, version_number, definition, outputs, definition_hash,
                     input_refs, source_draft_revision, created_by)
                VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, ?, '[]'::jsonb, 1, ?)
                """, pipelineVersionId, pipelineId, "hash-" + UUID.randomUUID(), ownerId);
        jdbc.update("""
                INSERT INTO data.pipeline_build
                    (id, pipeline_id, pipeline_version_id, status, trigger_type, operation_id,
                     resolved_inputs, stats)
                VALUES (?, ?, ?, 'succeeded', 'manual', ?, '[]'::jsonb, '{}'::jsonb)
                """, buildId, pipelineId, pipelineVersionId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.pipeline_output (id, pipeline_id, node_id, output_type, name)
                VALUES (?, ?, ?, 'dataset', ?)
                """, outputId, pipelineId, UUID.randomUUID(), name);
        jdbc.update("""
                INSERT INTO data.pipeline_build_output
                    (id, build_id, output_id, output_type, status, result, stats)
                VALUES (?, ?, ?, 'dataset', 'succeeded', '{}'::jsonb, '{}'::jsonb)
                """, buildOutputId, buildId, outputId);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset
                    (id, output_id, pipeline_id, name)
                VALUES (?, ?, ?, ?)
                """, datasetId, outputId, pipelineId, name);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset_version
                    (id, dataset_id, build_output_id, version_number, iceberg_catalog,
                     iceberg_namespace, iceberg_table, iceberg_snapshot_id, schema_json,
                     row_count, byte_size, committed_at, published_at)
                VALUES (?, ?, ?, 1, 'ontoweave', 'pipeline_outputs', ?, 21,
                        CAST(? AS jsonb), 3, 256, now(), now())
                """, versionId, datasetId, buildOutputId, name, schema());
        jdbc.update("UPDATE data.pipeline_dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return datasetId;
    }

    private String schema() {
        return """
                {"fields":[
                  {"name":"id","type":"long","required":true},
                  {"name":"name","type":"string","required":false}
                ]}
                """;
    }
}
