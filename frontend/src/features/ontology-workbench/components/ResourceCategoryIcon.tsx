import { Boxes, KeyRound, Share2, type LucideIcon } from 'lucide-react'
import type { ResourceCategory } from '../lib/constants'

const CATEGORY_ICONS: Record<ResourceCategory, LucideIcon> = {
  object_type: Boxes,
  foreign_key: KeyRound,
  many_to_many: Share2,
}

interface ResourceCategoryIconProps {
  category: ResourceCategory
  size?: number
}

export function ResourceCategoryIcon({ category, size = 15 }: ResourceCategoryIconProps) {
  const Icon = CATEGORY_ICONS[category]
  return <Icon size={size} aria-hidden="true" />
}
