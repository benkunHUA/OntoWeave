import {
  Checkbox,
  NativeSelect,
  NumberInput,
  PasswordInput,
  Radio,
  Select,
  Textarea,
  TextInput,
  type CheckboxProps,
  type NativeSelectProps,
  type NumberInputProps,
  type PasswordInputProps,
  type RadioProps,
  type SelectProps,
  type TextareaProps,
  type TextInputProps,
} from '@mantine/core'
import { Search } from 'lucide-react'

export function TextField(props: TextInputProps) {
  return <TextInput {...props} />
}

export function TextAreaField(props: TextareaProps) {
  return <Textarea {...props} />
}

export function SelectField(props: SelectProps) {
  return <Select {...props} />
}

export function NativeSelectField(props: NativeSelectProps) {
  return <NativeSelect {...props} />
}

export function NumberField(props: NumberInputProps) {
  return <NumberInput {...props} />
}

export function PasswordField(props: PasswordInputProps) {
  return <PasswordInput {...props} />
}

export function CheckboxField(props: CheckboxProps) {
  return <Checkbox {...props} />
}

export function RadioField(props: RadioProps) {
  return <Radio {...props} />
}

export function SearchField(props: TextInputProps) {
  return <TextInput leftSection={<Search size={14} />} size="xs" {...props} />
}
