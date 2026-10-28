import { useEffect, useState } from 'react'
import { getOntologyResourceDefinition } from '../../../shared/api/ontology'
import type { QueryEvidenceItem } from '../lib/evidence'

export interface EvidenceTypeMetadata {
  name: string | null
  propertyNames: Record<string, string>
}

type MetadataMap = Record<string, EvidenceTypeMetadata>

const metadataCache = new Map<string, Promise<EvidenceTypeMetadata | null>>()

/** 旧消息只存了属性 ID；按对象类型补查本体定义，补全标题和字段名。 */
export function useEvidenceMetadata(evidence: QueryEvidenceItem[]): MetadataMap {
  const [metadata, setMetadata] = useState<MetadataMap>({})
  const requestKey = unresolvedTypeIds(evidence).join('|')

  useEffect(() => {
    if (!requestKey) return
    let cancelled = false
    void Promise.all(requestKey.split('|').map(loadMetadata)).then((results) => {
      if (cancelled) return
      const next = Object.fromEntries(
        results.filter((entry): entry is [string, EvidenceTypeMetadata] => entry !== null),
      )
      if (Object.keys(next).length) {
        setMetadata((current) => ({ ...current, ...next }))
      }
    })
    return () => {
      cancelled = true
    }
  }, [requestKey])

  return metadata
}

function unresolvedTypeIds(evidence: QueryEvidenceItem[]): string[] {
  return [...new Set(evidence.filter(needsMetadata).map((item) => item.objectTypeId).filter(Boolean))]
}

function needsMetadata(item: QueryEvidenceItem): boolean {
  if (!item.objectTypeName) return true
  return Object.keys(item.properties).some((key) => !item.propertyNames[key])
}

function loadMetadata(objectTypeId: string): Promise<[string, EvidenceTypeMetadata] | null> {
  let request = metadataCache.get(objectTypeId)
  if (!request) {
    request = getOntologyResourceDefinition('object_type', objectTypeId)
      .then((definition) => ({
        name: definition.name.trim() || null,
        propertyNames: Object.fromEntries(
          definition.properties.map((property) => [property.id, property.name]),
        ),
      }))
      .catch(() => null)
    metadataCache.set(objectTypeId, request)
  }
  return request.then((value) => (value ? [objectTypeId, value] : null))
}
