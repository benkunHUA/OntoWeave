package com.ontoweave.data.ontology.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

final class P3FunctionalFixture {

    private final UUID ownerId;
    private final UUID orderTypeId;
    private final UUID customerTypeId;
    private final UUID productTypeId;
    private final String orderMapping;
    private final String relationMapping;
    private final P3AcceptanceSupport.ObjectBindingFixture orderBinding;
    private final P3AcceptanceSupport.ObjectBindingFixture productBinding;
    private final P3AcceptanceSupport.RelationBindingFixture m2mBinding;
    private final UUID orderV1VersionId;
    private final UUID orderV2VersionId;
    private final UUID fkRelationId;
    private final UUID m2mRelationId;
    private final UUID failedRunId;

    private P3FunctionalFixture(
            UUID ownerId,
            UUID orderTypeId,
            UUID customerTypeId,
            UUID productTypeId,
            String orderMapping,
            String relationMapping,
            P3AcceptanceSupport.ObjectBindingFixture orderBinding,
            P3AcceptanceSupport.ObjectBindingFixture productBinding,
            P3AcceptanceSupport.RelationBindingFixture m2mBinding,
            UUID orderV1VersionId,
            UUID orderV2VersionId,
            UUID fkRelationId,
            UUID m2mRelationId,
            UUID failedRunId
    ) {
        this.ownerId = ownerId;
        this.orderTypeId = orderTypeId;
        this.customerTypeId = customerTypeId;
        this.productTypeId = productTypeId;
        this.orderMapping = orderMapping;
        this.relationMapping = relationMapping;
        this.orderBinding = orderBinding;
        this.productBinding = productBinding;
        this.m2mBinding = m2mBinding;
        this.orderV1VersionId = orderV1VersionId;
        this.orderV2VersionId = orderV2VersionId;
        this.fkRelationId = fkRelationId;
        this.m2mRelationId = m2mRelationId;
        this.failedRunId = failedRunId;
    }

    static P3FunctionalFixture build(
            P3AcceptanceSupport support,
            IcebergSnapshotReader snapshots,
            ObjectMapper mapper
    ) {
        support.truncateOntology();
        when(snapshots.open(any(), anyLong())).thenAnswer(invocation ->
                P3SnapshotData.read(invocation.getArgument(0), invocation.getArgument(1)));

        UUID ownerId = UUID.randomUUID();
        UUID orderTypeId = UUID.randomUUID();
        UUID customerTypeId = UUID.randomUUID();
        UUID productTypeId = UUID.randomUUID();
        String orderMapping = P3FunctionalModels.orderMappingJson(mapper);
        String relationMapping = P3FunctionalModels.relationMappingJson(mapper);

        P3AcceptanceSupport.DatasetFixture customers = support.createDataset(
                ownerId, P3SnapshotData.CUSTOMER_TABLE, 101L,
                P3SnapshotData.CUSTOMER_ROWS, 3);
        P3AcceptanceSupport.DatasetFixture products = support.createDataset(
                ownerId, P3SnapshotData.PRODUCT_TABLE, 102L,
                P3SnapshotData.PRODUCT_ROWS, 2);
        P3AcceptanceSupport.DatasetFixture orders = support.createDataset(
                ownerId, P3SnapshotData.ORDER_TABLE, P3SnapshotData.ORDER_V1_SNAPSHOT,
                P3SnapshotData.ORDER_V1_ROWS, 8);
        P3AcceptanceSupport.DatasetFixture orderItems = support.createDataset(
                ownerId, P3SnapshotData.ORDER_ITEM_TABLE, 4_001L,
                P3SnapshotData.ORDER_ITEM_ROWS, 2);

        P3AcceptanceSupport.ObjectBindingFixture customerBinding = support.createObjectBinding(
                ownerId, customerTypeId, customers,
                P3FunctionalModels.customerMappingJson(mapper));
        P3AcceptanceSupport.ObjectBindingFixture productBinding = support.createObjectBinding(
                ownerId, productTypeId, products,
                P3FunctionalModels.productMappingJson(mapper));
        P3AcceptanceSupport.ObjectBindingFixture orderBinding = support.createObjectBinding(
                ownerId, orderTypeId, orders, orderMapping);

        UUID fkRelationId = UUID.randomUUID();
        insertRelationDefinition(support, ownerId, fkRelationId, orderTypeId, customerTypeId,
                "placed_by", "one_to_many", P3FunctionalModels.ORDER_CUSTOMER_ID, "integer",
                P3FunctionalModels.ORDER_ID, "integer",
                P3FunctionalModels.CUSTOMER_ID, "integer");

        executeSucceeded(support, support.insertRunningObjectRun(
                ownerId, customerTypeId, customerBinding.bindingId(),
                customers.versionId(), customerBinding.mapping()));
        executeSucceeded(support, support.insertRunningObjectRun(
                ownerId, productTypeId, productBinding.bindingId(),
                products.versionId(), productBinding.mapping()));
        executeSucceeded(support, support.insertRunningObjectRun(
                ownerId, orderTypeId, orderBinding.bindingId(),
                orders.versionId(), orderMapping));
        UUID orderV1VersionId = requireCurrentVersion(support, orderBinding.bindingId(), "order v1");

        UUID orderV2DatasetVersion = support.addDatasetVersion(
                orders, P3SnapshotData.ORDER_V2_SNAPSHOT, P3SnapshotData.ORDER_V2_ROWS, 8);
        executeSucceeded(support, support.insertRunningObjectRun(
                ownerId, orderTypeId, orderBinding.bindingId(),
                orderV2DatasetVersion, orderMapping));
        UUID orderV2VersionId = requireCurrentVersion(support, orderBinding.bindingId(), "order v2");

        UUID m2mRelationId = UUID.randomUUID();
        insertRelationDefinition(support, ownerId, m2mRelationId, orderTypeId, productTypeId,
                "contains", "many_to_many", null, null,
                P3FunctionalModels.ORDER_ID, "integer",
                P3FunctionalModels.PRODUCT_ID, "integer");
        UUID initialRelationVersion = UUID.randomUUID();
        P3AcceptanceSupport.RelationBindingFixture m2mBinding = support.createRelationBinding(
                ownerId, m2mRelationId, orderItems, relationMapping, initialRelationVersion);
        insertInitialManyToManyVersion(
                support, ownerId, m2mRelationId, initialRelationVersion, m2mBinding, orderItems,
                orderTypeId, productTypeId,
                orderV2VersionId,
                requireCurrentVersion(support, productBinding.bindingId(), "product v1"));
        executeSucceeded(support, support.insertRunningRelationRun(
                ownerId, m2mRelationId, m2mBinding.bindingId(),
                orderItems.versionId(), relationMapping));

        UUID duplicateDatasetVersion = support.addDatasetVersion(
                orders, P3SnapshotData.ORDER_DUPLICATE_SNAPSHOT,
                P3SnapshotData.ORDER_V2_ROWS, 8);
        OntologyIndexRunEntity failed = support.insertRunningObjectRun(
                ownerId, orderTypeId, orderBinding.bindingId(),
                duplicateDatasetVersion, orderMapping);
        support.executeObjectRun(failed);

        return new P3FunctionalFixture(
                ownerId, orderTypeId, customerTypeId, productTypeId,
                orderMapping, relationMapping, orderBinding, productBinding,
                m2mBinding, orderV1VersionId, orderV2VersionId,
                fkRelationId, m2mRelationId, failed.getId());
    }

    UUID ownerId() {
        return ownerId;
    }

    UUID orderTypeId() {
        return orderTypeId;
    }

    UUID customerTypeId() {
        return customerTypeId;
    }

    UUID productTypeId() {
        return productTypeId;
    }

    String orderMapping() {
        return orderMapping;
    }

    String relationMapping() {
        return relationMapping;
    }

    P3AcceptanceSupport.ObjectBindingFixture orderBinding() {
        return orderBinding;
    }

    P3AcceptanceSupport.RelationBindingFixture m2mBinding() {
        return m2mBinding;
    }

    UUID orderV1VersionId() {
        return orderV1VersionId;
    }

    UUID orderV2VersionId() {
        return orderV2VersionId;
    }

    UUID fkRelationId() {
        return fkRelationId;
    }

    UUID m2mRelationId() {
        return m2mRelationId;
    }

    UUID failedRunId() {
        return failedRunId;
    }

    private static void insertRelationDefinition(
            P3AcceptanceSupport support,
            UUID ownerId,
            UUID relationId,
            UUID sourceTypeId,
            UUID targetTypeId,
            String name,
            String cardinality,
            UUID foreignKeyPropertyId,
            String foreignKeyDatatype,
            UUID sourcePrimaryKeyAttributeId,
            String sourcePrimaryKeyDatatype,
            UUID targetPrimaryKeyAttributeId,
            String targetPrimaryKeyDatatype
    ) {
        support.jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     foreign_key_property_id, foreign_key_datatype,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, relationId, ownerId, sourceTypeId, targetTypeId, name, cardinality,
                foreignKeyPropertyId, foreignKeyDatatype, sourcePrimaryKeyAttributeId,
                sourcePrimaryKeyDatatype, targetPrimaryKeyAttributeId, targetPrimaryKeyDatatype);
    }

    private static void insertInitialManyToManyVersion(
            P3AcceptanceSupport support,
            UUID ownerId,
            UUID relationId,
            UUID versionId,
            P3AcceptanceSupport.RelationBindingFixture binding,
            P3AcceptanceSupport.DatasetFixture dataset,
            UUID orderTypeId,
            UUID productTypeId,
            UUID orderVersionId,
            UUID productVersionId
    ) {
        support.jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id,
                     from_object_version_id, to_object_version_id,
                     source_dataset_kind, source_dataset_id, source_dataset_version_id,
                     source_snapshot_id, mapping_snapshot, mapping_revision, run_id,
                     status, row_count, skipped_count, activated_at)
                VALUES (?, ?, ?, ?, 'MANY_TO_MANY', ?, ?, ?, ?, 'SYNC', ?, ?, 4001,
                        CAST(? AS jsonb), 1, ?, 'ACTIVE', 0, 0, now())
                """, versionId, ownerId, binding.bindingId(), relationId,
                orderTypeId, productTypeId, orderVersionId, productVersionId,
                dataset.datasetId(), dataset.versionId(), binding.mapping(), UUID.randomUUID());
    }

    private static void executeSucceeded(P3AcceptanceSupport support, OntologyIndexRunEntity run) {
        support.executeObjectRun(run);
        OntologyIndexRunEntity stored = support.runs.findById(run.getId()).orElseThrow();
        if (stored.getStatus() != RunStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "P3 acceptance fixture run failed: " + stored.getErrorCode()
                            + " " + stored.getErrorMessage());
        }
    }

    private static UUID requireCurrentVersion(
            P3AcceptanceSupport support,
            UUID bindingId,
            String label
    ) {
        UUID version = support.currentObjectVersion(bindingId);
        if (version == null) {
            throw new IllegalStateException(label + " has no current version");
        }
        return version;
    }
}
