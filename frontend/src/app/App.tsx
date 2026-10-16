import { QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import '../styles/app.css'
import '../styles/action.css'
import '../styles/dialog.css'
import '../styles/drawer.css'
import { HomePage } from '../features/home/views/HomePage'
import { queryClient } from '../shared/query/client'

const ModelingPage = lazy(() =>
  import('../features/modeling/views/ModelingPage').then((module) => ({
    default: module.ModelingPage,
  })),
)

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<div className="route-loading">正在打开工作台…</div>}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/modeling" element={<Navigate replace to="/" />} />
            <Route path="/modeling/:conversationId" element={<ModelingPage />} />
            <Route path="*" element={<Navigate replace to="/" />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
