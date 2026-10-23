package com.ontoweave.data.ontology.mapping;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.dataset.DatasetColumn;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.dataset.DatasetSchema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MappingValidator {

    private final RelationMappingValidator relationValidator = new RelationMappingValidator();

    public MappingValidationResult validate(
            MappingSnapshot mapping,
            DatasetRef dataset,
            List<OntologyAttributeDefinition> definitions,
            String subjectStatus
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
        if (!mapping.isObjectMapping() || mapping.isRelationMapping()) {
            issues.block(MappingIssueCode.MAPPING_KIND_INVALID, "不是对象映射", null, null);
            return issues.result();
        }

        Map<UUID, OntologyAttributeDefinition> definitionById = definitionById(definitions);
        Map<String, DatasetColumn> columnByName = DatasetSchema.columnsByName(dataset.schemaJson());
        Map<UUID, MappingSnapshot.AttributeMapping> mappedById = new HashMap<>();
        Set<String> usedColumns = new HashSet<>();

        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            checkAttribute(attribute, definitionById, columnByName, mappedById, usedColumns, issues);
        }
        checkPrimaryKey(mapping, definitionById, mappedById, issues);
        checkUnmappedAttributes(definitions, mappedById, issues);
        checkTitle(mapping, mappedById, issues);
        return issues.result();
    }

    public MappingValidationResult validateRelation(
            MappingSnapshot mapping,
            DatasetRef dataset,
            String subjectStatus,
            String fromPrimaryKeyDatatype,
            String toPrimaryKeyDatatype
    ) {
        return relationValidator.validate(
                mapping,
                dataset,
                subjectStatus,
                fromPrimaryKeyDatatype,
                toPrimaryKeyDatatype
        );
    }

    private void checkPrimaryKey(
            MappingSnapshot mapping,
            Map<UUID, OntologyAttributeDefinition> definitionById,
            Map<UUID, MappingSnapshot.AttributeMapping> mappedById,
            MappingIssues issues
    ) {
        OntologyAttributeDefinition primaryKey = definitionById.get(mapping.primaryKeyAttributeId());
        if (mapping.primaryKeyAttributeId() == null || primaryKey == null) {
            issues.block(
                    MappingIssueCode.PRIMARY_KEY_NOT_FOUND,
                    "主键属性不存在",
                    mapping.primaryKeyAttributeId(),
                    null
            );
            return;
        }
        if (!OntologyTypeRules.isSupportedPrimaryKey(primaryKey.datatype())
                || primaryKey.isArray()) {
            issues.block(
                    MappingIssueCode.PRIMARY_KEY_TYPE_UNSUPPORTED,
                    "主键类型不受支持",
                    primaryKey.attributeId(),
                    null
            );
        }
        if (!mappedById.containsKey(primaryKey.attributeId())) {
            issues.block(
                    MappingIssueCode.PRIMARY_KEY_NOT_MAPPED,
                    "主键属性未映射",
                    primaryKey.attributeId(),
                    null
            );
        }
    }

    private void checkUnmappedAttributes(
            List<OntologyAttributeDefinition> definitions,
            Map<UUID, MappingSnapshot.AttributeMapping> mappedById,
            MappingIssues issues
    ) {
        for (OntologyAttributeDefinition definition : definitions) {
            if (definition == null || mappedById.containsKey(definition.attributeId())) {
                continue;
            }
            if (definition.required()) {
                issues.block(
                        MappingIssueCode.REQUIRED_ATTRIBUTE_UNMAPPED,
                        "必填属性未映射",
                        definition.attributeId(),
                        null
                );
            } else {
                issues.warn(
                        MappingIssueCode.OPTIONAL_ATTRIBUTE_UNMAPPED,
                        "可选属性未映射，生成时取空",
                        definition.attributeId(),
                        null
                );
            }
        }
    }

    private void checkTitle(
            MappingSnapshot mapping,
            Map<UUID, MappingSnapshot.AttributeMapping> mappedById,
            MappingIssues issues
    ) {
        if (mapping.titleKeyAttributeId() == null
                || !mappedById.containsKey(mapping.titleKeyAttributeId())) {
            issues.warn(
                    MappingIssueCode.TITLE_FALLBACK,
                    "标题为空时回退主键",
                    mapping.titleKeyAttributeId(),
                    null
            );
        }
    }

    private void checkAttribute(
            MappingSnapshot.AttributeMapping attribute,
            Map<UUID, OntologyAttributeDefinition> definitionById,
            Map<String, DatasetColumn> columnByName,
            Map<UUID, MappingSnapshot.AttributeMapping> mappedById,
            Set<String> usedColumns,
            MappingIssues issues
    ) {
        UUID attributeId = attribute.attributeId();
        String columnName = attribute.column();
        OntologyAttributeDefinition definition = definitionById.get(attributeId);
        if (definition == null) {
            issues.block(
                    MappingIssueCode.ATTRIBUTE_NOT_FOUND,
                    "本体属性不存在",
                    attributeId,
                    columnName
            );
            return;
        }
        if (mappedById.putIfAbsent(attributeId, attribute) != null) {
            issues.block(
                    MappingIssueCode.ATTRIBUTE_ALREADY_MAPPED,
                    "同一属性被重复映射",
                    attributeId,
                    columnName
            );
        }
        if (columnName == null || columnName.isBlank()) {
            issues.block(
                    MappingIssueCode.COLUMN_NOT_FOUND,
                    "映射列不存在",
                    attributeId,
                    columnName
            );
            return;
        }
        if (!usedColumns.add(columnName)) {
            issues.block(
                    MappingIssueCode.COLUMN_ALREADY_MAPPED,
                    "同一列被重复映射",
                    attributeId,
                    columnName
            );
        }
        DatasetColumn column = columnByName.get(columnName);
        if (column == null) {
            issues.block(
                    MappingIssueCode.COLUMN_NOT_FOUND,
                    "数据集字段不存在",
                    attributeId,
                    columnName
            );
            return;
        }
        if (!OntologyTypeRules.sameType(attribute.datatype(), definition.datatype())) {
            issues.block(
                    MappingIssueCode.TYPE_INCOMPATIBLE,
                    "映射 datatype 与本体定义不一致",
                    attributeId,
                    columnName
            );
        }
        if (definition.isArray() || attribute.isArray()) {
            issues.block(
                    MappingIssueCode.ARRAY_ATTRIBUTE_UNSUPPORTED,
                    "数组属性暂不支持映射",
                    attributeId,
                    columnName
            );
        }
        if (OntologyTypeRules.isNested(column.type())) {
            issues.block(
                    MappingIssueCode.ARRAY_ATTRIBUTE_UNSUPPORTED,
                    "数组或嵌套类型不可映射",
                    attributeId,
                    columnName
            );
            return;
        }
        if (!OntologyTypeRules.compatible(definition.datatype(), column.type())) {
            issues.block(
                    MappingIssueCode.TYPE_INCOMPATIBLE,
                    "属性类型与数据集字段类型不兼容",
                    attributeId,
                    columnName
            );
        }
        checkEnum(attribute, definition, issues);
    }

    private void checkEnum(
            MappingSnapshot.AttributeMapping attribute,
            OntologyAttributeDefinition definition,
            MappingIssues issues
    ) {
        if (!"enum".equals(OntologyTypeRules.normalize(definition.datatype()))) {
            return;
        }
        if (definition.enumValues() == null || definition.enumValues().isEmpty()) {
            issues.block(
                    MappingIssueCode.ENUM_VALUES_INVALID,
                    "枚举属性缺少候选值",
                    definition.attributeId(),
                    attribute.column()
            );
        }
        if (attribute.enumValues() == null || attribute.enumValues().isEmpty()) {
            issues.block(
                    MappingIssueCode.ENUM_VALUES_INVALID,
                    "映射未冻结枚举候选值",
                    attribute.attributeId(),
                    attribute.column()
            );
        }
    }

    private Map<UUID, OntologyAttributeDefinition> definitionById(
            List<OntologyAttributeDefinition> definitions
    ) {
        Map<UUID, OntologyAttributeDefinition> definitionById = new HashMap<>();
        if (definitions != null) {
            for (OntologyAttributeDefinition definition : definitions) {
                if (definition != null && definition.attributeId() != null) {
                    definitionById.put(definition.attributeId(), definition);
                }
            }
        }
        return definitionById;
    }
}
