import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { MantineProvider } from '@mantine/core'
import '@mantine/core/styles.css'
import '@xyflow/react/dist/style.css'
import './styles/global.css'
import App from './app/App.tsx'
import { mantineTheme } from './app/mantineTheme.ts'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider forceColorScheme="dark" theme={mantineTheme}>
      <App />
    </MantineProvider>
  </StrictMode>,
)
