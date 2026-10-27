import { QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import '@xyflow/react/dist/style.css'
import '../styles/app.css'
import '../styles/action.css'
import '../styles/dialog.css'
import '../styles/data-preview.css'
import '../styles/drawer.css'
import '../styles/data-connection.css'
import '../styles/data-connection-context.css'
import '../styles/data-pipeline.css'
import '../styles/data-pipeline-canvas.css'
import '../styles/data-pipeline-nodes.css'
import '../styles/data-pipeline-config.css'
import '../styles/data-pipeline-execution-preview.css'
import '../styles/data-pipeline-execution-build.css'
import '../styles/data-pipeline-execution-source.css'
import '../styles/data-pipeline-execution-schedule.css'
import '../styles/ontology-workbench.css'
import '../styles/ontology-workspace.css'
import '../styles/ontology-canvas.css'
import '../styles/ontology-relation.css'
import '../styles/ontology-data.css'
import '../styles/query.css'
import { HomePage } from '../features/home/views/HomePage'
import { queryClient } from '../shared/query/client'

const ModelingPage = lazy(() =>
  import('../features/modeling/views/ModelingPage').then((module) => ({
    default: module.ModelingPage,
  })),
)

const DataConnectionWorkbench = lazy(() =>
  import('../features/data-connection/views/DataConnectionWorkbench').then((module) => ({
    default: module.DataConnectionWorkbench,
  })),
)

const DataPipelineWorkbench = lazy(() =>
  import('../features/data-pipeline/views/DataPipelineWorkbench').then((module) => ({
    default: module.DataPipelineWorkbench,
  })),
)

const OntologyWorkbenchView = lazy(() =>
  import('../features/ontology-workbench/views/OntologyWorkbenchView').then((module) => ({
    default: module.OntologyWorkbenchView,
  })),
)

const QueryWorkbench = lazy(() =>
  import('../features/query/views/QueryWorkbench').then((module) => ({
    default: module.QueryWorkbench,
  })),
)

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<div className="route-loading">正在打开工作台…</div>}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/modeling" element={<ModelingPage />} />
            <Route path="/modeling/:conversationId" element={<ModelingPage />} />
            <Route path="/data-connections" element={<DataConnectionWorkbench />} />
            <Route path="/data-connections/:connectionId" element={<DataConnectionWorkbench />} />
            <Route path="/pipelines" element={<DataPipelineWorkbench />} />
            <Route path="/pipelines/:pipelineId" element={<DataPipelineWorkbench />} />
            <Route path="/ontology" element={<OntologyWorkbenchView />} />
            <Route path="/query" element={<QueryWorkbench />} />
            <Route path="/query/new" element={<QueryWorkbench />} />
            <Route path="/query/:conversationId" element={<QueryWorkbench />} />
            <Route path="*" element={<Navigate replace to="/" />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
