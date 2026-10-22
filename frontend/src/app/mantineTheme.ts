import {
  ActionIcon,
  Button,
  Checkbox,
  NativeSelect,
  NumberInput,
  PasswordInput,
  Radio,
  Select,
  Textarea,
  TextInput,
  createTheme,
  type MantineColorsTuple,
} from '@mantine/core'

const brand: MantineColorsTuple = [
  '#eef4ff',
  '#d9e6ff',
  '#b3cbff',
  '#8aadff',
  '#6693ff',
  '#3b7bff',
  '#2f66db',
  '#2451b3',
  '#1c3e8b',
  '#152c63',
]

export const mantineTheme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 5, dark: 5 },
  colors: { brand },
  fontFamily:
    'Inter, "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, BlinkMacSystemFont, sans-serif',
  fontFamilyMonospace: '"SFMono-Regular", Consolas, Monaco, monospace',
  defaultRadius: 'md',
  autoContrast: true,
  cursorType: 'pointer',
  components: {
    Button: Button.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    ActionIcon: ActionIcon.extend({
      defaultProps: {
        radius: 'md',
      },
    }),
    TextInput: TextInput.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    Textarea: Textarea.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    Select: Select.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    NativeSelect: NativeSelect.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    NumberInput: NumberInput.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    PasswordInput: PasswordInput.extend({
      defaultProps: {
        size: 'sm',
        radius: 'md',
      },
    }),
    Checkbox: Checkbox.extend({
      defaultProps: {
        size: 'sm',
        radius: 'sm',
      },
    }),
    Radio: Radio.extend({
      defaultProps: {
        size: 'sm',
      },
    }),
  },
})
