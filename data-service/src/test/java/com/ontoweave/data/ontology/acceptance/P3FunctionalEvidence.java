package com.ontoweave.data.ontology.acceptance;

import com.ontoweave.data.ontology.query.service.RelationQueryService;

import java.util.UUID;

final class P3FunctionalEvidence {

    private P3FunctionalEvidence() {
    }

    static void log(
            P3AcceptanceSupport support,
            P3FunctionalFixture fixture,
            RelationQueryService relationQueries
    ) {
        System.out.printf(
                "P3_FUNCTIONAL_COUNTS orderV1=%d orderV2=%d customers=%d products=%d "
                        + "fkInstancesAllVersions=%d m2mInstances=%d "
                        + "fkInstancesCurrent=%d m2mInstancesCurrent=%d "
                        + "orderValuesV1=%d orderValuesV2=%d failedRun=%s failedCode=%s%n",
                support.objectRows(fixture.orderV1VersionId()),
                support.objectRows(fixture.orderV2VersionId()),
                objectRows(support, fixture.customerTypeId()),
                objectRows(support, fixture.productTypeId()),
                relationRows(support, fixture.fkRelationId()),
                relationRows(support, fixture.m2mRelationId()),
                currentRelationRows(relationQueries, fixture, fixture.fkRelationId()),
                currentRelationRows(relationQueries, fixture, fixture.m2mRelationId()),
                valueRows(support, fixture.orderV1VersionId()),
                valueRows(support, fixture.orderV2VersionId()),
                fixture.failedRunId(),
                support.runs.findById(fixture.failedRunId()).orElseThrow().getErrorCode()
        );
    }

    private static long objectRows(P3AcceptanceSupport support, UUID objectTypeId) {
        return support.count("""
                SELECT count(*) FROM data.ontology_object_instance WHERE object_type_id = ?
                """, objectTypeId);
    }

    private static long relationRows(P3AcceptanceSupport support, UUID relationId) {
        return support.count("""
                SELECT count(*) FROM data.ontology_relation_instance WHERE relation_id = ?
                """, relationId);
    }

    private static long currentRelationRows(
            RelationQueryService relationQueries,
            P3FunctionalFixture fixture,
            UUID relationId
    ) {
        return relationQueries.instances(
                relationId, fixture.ownerId(), null, null, 1, 20).total();
    }

    private static long valueRows(P3AcceptanceSupport support, UUID versionId) {
        return support.count("""
                SELECT count(*) FROM data.ontology_object_value WHERE version_id = ?
                """, versionId);
    }
}
