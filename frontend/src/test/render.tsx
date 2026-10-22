import { MantineProvider } from '@mantine/core'
import {
  render as testingRender,
  type RenderOptions,
} from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { mantineTheme } from '../app/mantineTheme'

export {
  act,
  cleanup,
  fireEvent,
  renderHook,
  screen,
  waitFor,
  within,
} from '@testing-library/react'

function TestProviders({ children }: { children: ReactNode }) {
  return (
    <MantineProvider forceColorScheme="dark" theme={mantineTheme}>
      {children}
    </MantineProvider>
  )
}

export function render(ui: ReactElement, options?: Omit<RenderOptions, 'wrapper'>) {
  return testingRender(ui, { wrapper: TestProviders, ...options })
}

export function renderStatic(ui: ReactElement) {
  return renderToStaticMarkup(<TestProviders>{ui}</TestProviders>)
}
