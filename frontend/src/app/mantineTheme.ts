import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Checkbox,
  NavLink,
  NativeSelect,
  NumberInput,
  PasswordInput,
  Radio,
  Select,
  Table,
  Tabs,
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

const dark: MantineColorsTuple = [
  '#e8e8e8',
  '#c8c8c8',
  '#9c9c9c',
  '#6f6f6f',
  '#424242',
  '#3b3b3b',
  '#323232',
  '#2a2a2a',
  '#1f1f1f',
  '#1a1a1a',
]

export const mantineTheme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 5, dark: 5 },
  colors: { brand, dark },
  fontFamily:
    'Inter, "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, BlinkMacSystemFont, sans-serif',
  fontFamilyMonospace: '"SFMono-Regular", Consolas, Monaco, monospace',
  fontSizes: {
    xs: '0.75rem',
    sm: '0.8125rem',
    md: '0.875rem',
    lg: '1rem',
    xl: '1.125rem',
  },
  headings: {
    sizes: {
      h1: { fontSize: '2.25rem', lineHeight: '1.15' },
      h2: { fontSize: '1.5rem', lineHeight: '1.25' },
      h3: { fontSize: '1.125rem', lineHeight: '1.35' },
      h4: { fontSize: '0.9375rem', lineHeight: '1.4' },
    },
  },
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
    Badge: Badge.extend({
      defaultProps: {
        size: 'sm',
        radius: 'sm',
        variant: 'light',
      },
    }),
    Card: Card.extend({
      defaultProps: {
        withBorder: true,
        radius: 'lg',
        padding: 'md',
      },
    }),
    Table: Table.extend({
      defaultProps: {
        highlightOnHover: true,
        horizontalSpacing: 'sm',
        verticalSpacing: 'sm',
      },
    }),
    Tabs: Tabs.extend({
      defaultProps: {
        color: 'brand',
        variant: 'pills',
      },
    }),
    NavLink: NavLink.extend({
      defaultProps: {
        color: 'brand',
      },
    }),
  },
})
