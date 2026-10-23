package com.ontoweave.data.ontology.mapping;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.dataset.DatasetColumn;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.dataset.DatasetSchema;

import java.util.Map;

final class RelationMappingValidator {

    MappingValidationResult validate(
            MappingSnapshot mapping,
            DatasetRef dataset,
            String subjectStatus,
            String fromPrimaryKeyDatatype,
            String toPrimaryKeyDatatype
    ) {
        MappingIssues issues = new MappingIssues();
        if (!MappingSubjectStatus.isConfirmed(subjectStatus)) {
            issues.block(
                    MappingIssueCode.SUBJECT_NOT_CONFIRMED,
                    "对象类型或关系尚未确认",
                    null,
                    null
            );
        }
        if (mapping == null) {
            issues.block(MappingIssueCode.MAPPING_EMPTY, "映射为空", null, null);
            return issues.result();
        }
        if (dataset == null) {
            issues.block(MappingIssueCode.DATASET_NOT_FOUND, "数据集不可用", null, null);
            return issues.result();
        }
        if (!mapping.isRelationMapping() || mapping.isObjectMapping()) {
            issues.block(MappingIssueCode.MAPPING_KIND_INVALID, "不是多对多映射", null, null);
            return issues.result();
        }

        Map<String, DatasetColumn> columns = DatasetSchema.columnsByName(dataset.schemaJson());
        checkKey(
                "fromKeyColumn",
                mapping.fromKeyColumn(),
                mapping.fromKeyDatatype(),
                fromPrimaryKeyDatatype,
                columns,
                issues
        );
        checkKey(
                "toKeyColumn",
                mapping.toKeyColumn(),
                mapping.toKeyDatatype(),
                toPrimaryKeyDatatype,
                columns,
                issues
        );
        if (mapping.fromKeyColumn() != null
                && mapping.fromKeyColumn().equals(mapping.toKeyColumn())) {
            issues.block(
                    MappingIssueCode.COLUMN_ALREADY_MAPPED,
                    "连接表两端不能使用同一列",
                    null,
                    mapping.fromKeyColumn()
            );
        }
        return issues.result();
    }

    private void checkKey(
            String side,
            String columnName,
            String mappingDatatype,
            String primaryKeyDatatype,
            Map<String, DatasetColumn> columns,
            MappingIssues issues
    ) {
        if (columnName == null || columnName.isBlank()) {
            issues.block(MappingIssueCode.COLUMN_NOT_FOUND, side + " 未映射", null, columnName);
            return;
        }
        if (!OntologyTypeRules.isSupportedPrimaryKey(primaryKeyDatatype)) {
            issues.block(
                    MappingIssueCode.PRIMARY_KEY_TYPE_UNSUPPORTED,
                    side + " 对端主键类型不受支持",
                    null,
                    columnName
            );
        }
        if (!OntologyTypeRules.sameType(mappingDatatype, primaryKeyDatatype)) {
            issues.block(
                    MappingIssueCode.TYPE_INCOMPATIBLE,
                    side + " datatype 与对端主键不一致",
                    null,
                    columnName
            );
        }
        DatasetColumn column = columns.get(columnName);
        if (column == null) {
            issues.block(
                    MappingIssueCode.COLUMN_NOT_FOUND,
                    side + " 数据集字段不存在",
                    null,
                    columnName
            );
            return;
        }
        if (OntologyTypeRules.isNested(column.type())
                || !OntologyTypeRules.compatible(primaryKeyDatatype, column.type())) {
            issues.block(
                    MappingIssueCode.TYPE_INCOMPATIBLE,
                    side + " 数据集字段与对端主键类型不兼容",
                    null,
                    columnName
            );
        }
    }
}
