export {
  PROPERTY_DATATYPE_LABELS,
  emptyObjectMapping,
  emptyRelationMapping,
  incompatibleTypeMessage,
  isDatasetTypeCompatible,
  primaryKeyProperty,
  requiredAttributeIds,
  sanitizeObjectMapping,
  sanitizeRelationMapping,
  splitDatasetType,
  supportsPrimaryKey,
  titleKeyProperty,
  type MappingMode,
  type RelationEndpoint,
} from './mappingTypes'
export {
  connectObjectAttribute,
  connectRelationEndpoint,
  normalizeConnectionHandles,
  removeObjectConnection,
  removeRelationConnection,
  type MappingConnection,
} from './mappingConnections'
export { dedupeIssues, localObjectIssues, localRelationIssues } from './mappingIssues'
export {
  mergeObjectSuggestion,
  mergeRelationSuggestion,
  type SuggestionMerge,
} from './mappingSuggestions'
