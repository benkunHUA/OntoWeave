import { FieldMultiSelect } from './FieldMultiSelect'

interface GroupByPickerProps {
  value: unknown
  options?: string[]
  onChange: (value: string[]) => void
}

export function GroupByPicker(props: GroupByPickerProps) {
  return (
    <div data-editor="group-by-picker">
      <FieldMultiSelect {...props} />
    </div>
  )
}
