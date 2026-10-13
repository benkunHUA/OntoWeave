import {
  CircleAlert,
  CircleCheck,
  LoaderCircle,
  MessageSquarePlus,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Send,
  Sparkles,
  Square,
  Trash2,
  X,
} from 'lucide-react'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  confirmChangeset,
  createConversation,
  deleteConversation,
  editGraphElements,
  getChangeset,
  getConversation,
  getGraphSnapshot,
  getViewState,
  listChangesets,
  listConversationMessages,
  listConversations,
  rejectChangeset,
  renameConversation,
  saveViewState,
  streamAgentRun,
} from '../../../shared/api/client'
import { DeleteConversationDialog } from '../components/dialogs/DeleteConversationDialog'
import { RenameConversationDialog } from '../components/dialogs/RenameConversationDialog'
import { ChangesetBar } from '../components/ChangesetBar'
import { ChatNotice } from '../components/ChatNotice'
import { InterruptCard } from '../components/InterruptCard'
import { NodeDrawer, type NodeDrawerSubmit } from '../components/NodeDrawer'
import { OntologyCanvas } from '../components/OntologyCanvas'
import { RelationDrawer } from '../components/RelationDrawer'
import { MarkdownText } from '../components/MarkdownText'
import { ThinkingBlock } from '../components/ThinkingBlock'
import { ToolCallBlock } from '../components/ToolCallBlock'
import { formatOpSummary, hasPendingOntologyChanges } from '../lib/changesetSummary'
import { DEFAULT_DELETE_SCOPE } from '../lib/deleteScopes'
import { INTERRUPT_PLACEHOLDER } from '../lib/interruptCopy'
import {
  buildElementEdits,
  pendingPropertyDeleteIds,
  type NodeDraft,
} from '../lib/nodeDetails'
import {
  buildRelationEdits,
  deleteRelationEdit,
  type ForeignKeyCandidate,
  type RelationDraft,
} from '../lib/relationDetails'
import {
  blocksFromMessage,
  isNoticeMessage,
  pendingInterruptFrom,
  reduceBlocks,
  type PendingInterrupt,
} from '../lib/messageBlocks'
import type {
  AgentEvent,
  AgentRunInput,
  Changeset,
  ChatMessage,
  Conversation,
  DeleteScope,
  GraphEditOp,
  GraphSnapshot,
  InterruptOption,
  MessageBlock,
  ViewSpec,
} from '../../../shared/types'

function messageId() {
  return crypto.randomUUID()
}

const PANE_WIDTHS_STORAGE_KEY = 'ontoweave.pane-widths'
const DEFAULT_PANE_WIDTHS = { conversations: 268, chat: 400 }
const PANE_MIN_WIDTHS = { conversations: 200, chat: 320 }
const PANE_MAX_WIDTHS = { conversations: 460, chat: 760 }
type PaneKey = keyof typeof DEFAULT_PANE_WIDTHS
type PaneWidths = typeof DEFAULT_PANE_WIDTHS

function clampPaneWidth(key: PaneKey, value: number) {
  return Math.min(Math.max(value, PANE_MIN_WIDTHS[key]), PANE_MAX_WIDTHS[key])
}

function readPaneWidths(): PaneWidths {
  try {
    const raw = window.localStorage.getItem(PANE_WIDTHS_STORAGE_KEY)
    if (!raw) return DEFAULT_PANE_WIDTHS
    const parsed = JSON.parse(raw) as Partial<Record<PaneKey, number>>
    return {
      conversations: clampPaneWidth(
        'conversations',
        Number(parsed.conversations) || DEFAULT_PANE_WIDTHS.conversations,
      ),
      chat: clampPaneWidth('chat', Number(parsed.chat) || DEFAULT_PANE_WIDTHS.chat),
    }
  } catch {
    return DEFAULT_PANE_WIDTHS
  }
}

export function ModelingPage() {
  const navigate = useNavigate()
  const { conversationId } = useParams()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeConversation, setActiveConversation] = useState<Conversation | null>(null)
  const [snapshot, setSnapshot] = useState<GraphSnapshot | null>(null)
  const [viewSpec, setViewSpec] = useState<ViewSpec>({})
  const [changeset, setChangeset] = useState<Changeset | null>(null)
  const [messagesByConversation, setMessagesByConversation] = useState<
    Record<string, ChatMessage[]>
  >({})
  const [input, setInput] = useState('')
  const [isRunning, setIsRunning] = useState(false)
  const [interrupt, setInterrupt] = useState<PendingInterrupt | null>(null)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [selectedRelationId, setSelectedRelationId] = useState<string | null>(null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [paneWidths, setPaneWidths] = useState<PaneWidths>(readPaneWidths)
  const [draggingPane, setDraggingPane] = useState<PaneKey | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Conversation | null>(null)
  const [deleteMode, setDeleteMode] = useState<DeleteScope>(DEFAULT_DELETE_SCOPE)
  const [isDeleting, setIsDeleting] = useState(false)
  const [renameOpen, setRenameOpen] = useState(false)
  const [draftConversationTitle, setDraftConversationTitle] = useState('')
  const [isRenaming, setIsRenaming] = useState(false)
  const [isConfirming, setIsConfirming] = useState(false)
  const [isSavingNode, setIsSavingNode] = useState(false)
  const [nodeSaveError, setNodeSaveError] = useState<string | null>(null)
  // 保存成功后自增，用它当抽屉的 key：重新按服务端最新数据初始化草稿，
  // 新增字段行因此拿到真实 id，不会被重复提交。
  const [nodeSavedToken, setNodeSavedToken] = useState(0)
  const [isSavingRelation, setIsSavingRelation] = useState(false)
  const [relationSaveError, setRelationSaveError] = useState<string | null>(null)
  const [relationSavedToken, setRelationSavedToken] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const saveTimerRef = useRef<number | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const resizeRef = useRef<{ key: PaneKey; startX: number; startWidth: number } | null>(null)

  const messages = useMemo(
    () =>
      activeConversation
        ? (messagesByConversation[activeConversation.id] ?? [])
        : [],
    [activeConversation, messagesByConversation],
  )
  const selectedNode =
    snapshot?.nodes.find((node) => node.id === selectedNodeId) ?? null
  const selectedRelation =
    snapshot?.relations.find((relation) => relation.id === selectedRelationId) ?? null
  const selectedRelationEndpoints = useMemo(() => {
    if (!selectedRelation || !snapshot) return null
    const nameOf = (id: string) =>
      snapshot.nodes.find((node) => node.id === id)?.name ?? '未知对象'
    return {
      source: nameOf(selectedRelation.source_type_id),
      target: nameOf(selectedRelation.target_type_id),
    }
  }, [selectedRelation, snapshot])
  // 外键属性候选：关系两端的对象类型各自持有的属性。
  const selectedRelationForeignKeyCandidates = useMemo<ForeignKeyCandidate[]>(() => {
    if (!selectedRelation || !snapshot) return []
    const candidates: ForeignKeyCandidate[] = []
    for (const typeId of [
      selectedRelation.source_type_id,
      selectedRelation.target_type_id,
    ]) {
      const node = snapshot.nodes.find((item) => item.id === typeId)
      if (!node) continue
      for (const property of node.properties) {
        candidates.push({ value: property.id, label: `${node.name} · ${property.name}` })
      }
    }
    return candidates
  }, [selectedRelation, snapshot])
  const opSummary = useMemo(() => formatOpSummary(changeset), [changeset])
  const pendingDeleteIds = useMemo(() => pendingPropertyDeleteIds(changeset), [changeset])

  const appendMessage = useCallback((targetId: string, message: ChatMessage) => {
    setMessagesByConversation((current) => ({
      ...current,
      [targetId]: [...(current[targetId] ?? []), message],
    }))
  }, [])

  const patchMessage = useCallback(
    (targetId: string, messageIdToPatch: string, patch: Partial<ChatMessage>) => {
      setMessagesByConversation((current) => ({
        ...current,
        [targetId]: (current[targetId] ?? []).map((message) =>
          message.id === messageIdToPatch ? { ...message, ...patch } : message,
        ),
      }))
    },
    [],
  )

  const loadConversations = useCallback(async () => {
    const items = await listConversations()
    setConversations(items)
    return items
  }, [])

  const loadWorkspace = useCallback(
    async (targetId: string, options: { keepSelection?: boolean } = {}) => {
      const conversation = await getConversation(targetId)
      const [nextSnapshot, nextViewState, changesets, history] = await Promise.all([
        getGraphSnapshot(conversation.graph_id),
        getViewState(conversation.graph_id),
        listChangesets(conversation.graph_id),
        listConversationMessages(conversation.id),
      ])
      const open = changesets.find((item) => item.status === 'open')
      const detail = open ? await getChangeset(open.id) : null

      setActiveConversation(conversation)
      setConversations((current) =>
        current.some((item) => item.id === conversation.id)
          ? current
          : [conversation, ...current],
      )
      setSnapshot(nextSnapshot)
      setViewSpec(nextViewState.spec ?? {})
      setChangeset(detail)
      if (!options.keepSelection) {
        setSelectedNodeId(null)
        setSelectedRelationId(null)
      }
      const restored = history.map((message) => ({
        id: message.id,
        role: message.role,
        blocks: blocksFromMessage(message),
        kind: message.kind,
      }))
      setMessagesByConversation((current) => ({
        ...current,
        [conversation.id]: restored,
      }))
      // 刷新后从这里重建「等待回答」，用户不用重新问一遍。
      setInterrupt(pendingInterruptFrom(history))
    },
    [],
  )

  useEffect(() => {
    void (async () => {
      try {
        await loadConversations()
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : '无法加载会话')
      }
    })()
  }, [loadConversations])

  useEffect(() => {
    if (!conversationId) return
    void (async () => {
      try {
        await loadWorkspace(conversationId)
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : '无法加载工作区')
      }
    })()
  }, [conversationId, loadWorkspace])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, interrupt])

  useEffect(() => {
    if (!draggingPane) return
    function handlePointerMove(event: PointerEvent) {
      const drag = resizeRef.current
      if (!drag) return
      const delta = event.clientX - drag.startX
      setPaneWidths((current) => ({
        ...current,
        [drag.key]: clampPaneWidth(drag.key, drag.startWidth + delta),
      }))
    }
    function handlePointerUp() {
      setDraggingPane(null)
    }
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [draggingPane])

  useEffect(() => {
    try {
      window.localStorage.setItem(PANE_WIDTHS_STORAGE_KEY, JSON.stringify(paneWidths))
    } catch {
      // 隐私模式等场景下 localStorage 不可用，忽略。
    }
  }, [paneWidths])

  async function startConversation() {
    const conversation = await createConversation('新对话')
    setConversations((current) => [conversation, ...current])
    navigate(`/modeling/${conversation.id}`)
  }

  function openRenameDialog() {
    if (!activeConversation) return
    setDraftConversationTitle(activeConversation.title || '')
    setRenameOpen(true)
  }

  async function submitRename() {
    if (!activeConversation) return
    const title = draftConversationTitle.trim()
    if (!title) return
    setIsRenaming(true)
    setError(null)
    try {
      const updated = await renameConversation(activeConversation.id, title)
      setActiveConversation(updated)
      setConversations((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setSnapshot((current) =>
        current
          ? { ...current, graph: { ...current.graph, name: updated.title } }
          : current,
      )
      setRenameOpen(false)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '会话重命名失败')
    } finally {
      setIsRenaming(false)
    }
  }

  const persistView = useCallback(
    (spec: ViewSpec, delay = 0) => {
      if (!activeConversation) return
      if (saveTimerRef.current !== null) {
        window.clearTimeout(saveTimerRef.current)
      }
      saveTimerRef.current = window.setTimeout(() => {
        void saveViewState(activeConversation.graph_id, spec).catch(
          (reason: unknown) => {
            setError(reason instanceof Error ? reason.message : '视图保存失败')
          },
        )
      }, delay)
    },
    [activeConversation],
  )

  function handleLayoutChange(kind: NonNullable<ViewSpec['layout']>['kind']) {
    const next = {
      ...viewSpec,
      layout: { kind, options: {} },
      nodes: {},
    }
    setViewSpec(next)
    persistView(next)
  }

  function handleMoveNode(nodeId: string, x: number, y: number) {
    const next = {
      ...viewSpec,
      nodes: {
        ...(viewSpec.nodes ?? {}),
        [nodeId]: { x, y },
      },
    }
    setViewSpec(next)
    persistView(next, 500)
  }

  function handleSelectNode(nodeId: string | null) {
    setSelectedNodeId(nodeId)
    setNodeSaveError(null)
    // 一次只开一个抽屉：选中对象就收掉关系面板，点空白则两个都关。
    setSelectedRelationId(null)
    setRelationSaveError(null)
  }

  function handleSelectRelation(relationId: string | null) {
    setSelectedRelationId(relationId)
    setRelationSaveError(null)
    setSelectedNodeId(null)
    setNodeSaveError(null)
  }

  function closeDrawer() {
    setSelectedNodeId(null)
    setNodeSaveError(null)
  }

  function closeRelationDrawer() {
    setSelectedRelationId(null)
    setRelationSaveError(null)
  }

  /** 两个抽屉共用的落库路径：登记草稿，需要时立刻确认并重取工作区。 */
  async function submitElementEdits(edits: GraphEditOp[], options: NodeDrawerSubmit) {
    if (!activeConversation) return
    const graphId = activeConversation.graph_id
    const updated = await editGraphElements(graphId, edits)
    if (options.confirm) {
      // 抽屉里直接确认：省掉再去左侧确认条点一次。确认会往会话追一条回执，
      // 所以整块工作区重取一遍，抽屉保持打开、按最新数据重挂。
      await confirmChangeset(updated.id)
      await loadWorkspace(activeConversation.id, { keepSelection: true })
    } else {
      setChangeset(updated)
      setSnapshot(await getGraphSnapshot(graphId))
    }
  }

  async function saveSelectedNode(draft: NodeDraft, options: NodeDrawerSubmit) {
    if (!activeConversation || !selectedNode) return
    const edits = buildElementEdits(selectedNode, draft)
    if (!edits.length) return
    setIsSavingNode(true)
    setNodeSaveError(null)
    try {
      await submitElementEdits(edits, options)
      setNodeSavedToken((token) => token + 1)
    } catch (reason) {
      setNodeSaveError(reason instanceof Error ? reason.message : '保存失败')
    } finally {
      setIsSavingNode(false)
    }
  }

  async function saveSelectedRelation(draft: RelationDraft, options: NodeDrawerSubmit) {
    if (!activeConversation || !selectedRelation) return
    const edits = buildRelationEdits(selectedRelation, draft)
    if (!edits.length) return
    await runRelationEdits(edits, options)
  }

  async function deleteSelectedRelation() {
    if (!activeConversation || !selectedRelation) return
    // 删除走幽灵态：先标记，用户在确认条上定稿。
    await runRelationEdits([deleteRelationEdit(selectedRelation.id)], { confirm: false })
  }

  async function runRelationEdits(edits: GraphEditOp[], options: NodeDrawerSubmit) {
    setIsSavingRelation(true)
    setRelationSaveError(null)
    try {
      await submitElementEdits(edits, options)
      setRelationSavedToken((token) => token + 1)
    } catch (reason) {
      setRelationSaveError(reason instanceof Error ? reason.message : '保存失败')
    } finally {
      setIsSavingRelation(false)
    }
  }

  async function confirmCurrentChanges() {
    if (!activeConversation || !changeset) return
    setIsConfirming(true)
    setError(null)
    try {
      await confirmChangeset(changeset.id)
      await loadWorkspace(activeConversation.id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '确认失败')
    } finally {
      setIsConfirming(false)
    }
  }

  async function handleReject() {
    if (!changeset || !activeConversation) return
    // 取消还没落库的视图保存：否则撤销把视图翻回去之后，这个 pending 的
    // PUT 又用旧 spec 把它覆盖回来，高亮就「撤销不掉」了。
    if (saveTimerRef.current !== null) {
      window.clearTimeout(saveTimerRef.current)
      saveTimerRef.current = null
    }
    try {
      await rejectChangeset(changeset.id)
      await loadWorkspace(activeConversation.id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '撤销失败')
    }
  }

  const runAgent = useCallback(
    async (targetId: string, body: AgentRunInput) => {
      let activeId: string = messageId()
      appendMessage(targetId, {
        id: activeId,
        role: 'assistant',
        blocks: [],
      })
      setIsRunning(true)
      setInterrupt(null)
      setError(null)
      const controller = new AbortController()
      abortRef.current = controller

      function replaceBlocks(update: (blocks: MessageBlock[]) => MessageBlock[]) {
        setMessagesByConversation((current) => ({
          ...current,
          [targetId]: (current[targetId] ?? []).map((message) =>
            message.id === activeId
              ? { ...message, blocks: update(message.blocks) }
              : message,
          ),
        }))
      }

      function failWith(text: string) {
        replaceBlocks(() => [{ id: messageId(), kind: 'text', text, done: true }])
        patchMessage(targetId, activeId, { kind: 'error' })
        setError(text)
      }

      function handleEvent(event: AgentEvent) {
        if (event.type === 'run.start') {
          // 服务端分配的消息 id 才是权威身份，用它替掉本地占位 id。
          const serverId = event.messageId
          if (serverId && serverId !== activeId) {
            const previous = activeId
            activeId = serverId
            setMessagesByConversation((current) => ({
              ...current,
              [targetId]: (current[targetId] ?? []).map((message) =>
                message.id === previous ? { ...message, id: serverId } : message,
              ),
            }))
          }
        } else if (event.type === 'state.update') {
          if (event.view && typeof event.view === 'object') {
            setViewSpec(event.view)
          }
        } else if (event.type === 'run.error') {
          failWith(String(event.message ?? '运行失败'))
        } else if (event.type === 'interrupt') {
          replaceBlocks((blocks) => reduceBlocks(blocks, event))
          setInterrupt({
            blockId: event.blockId,
            question: event.question ?? '需要你确认一下',
            options: event.options ?? [],
            freeText: event.freeText ?? null,
          })
        } else if (event.type === 'tool.end') {
          // ask_user / 工具审批是续跑后才拿到结果的，块留在更早的那条消息里，
          // 不能只扫当前这条，否则上一轮那块会一直显示成还在等。
          setMessagesByConversation((current) => ({
            ...current,
            [targetId]: (current[targetId] ?? []).map((message) =>
              message.blocks.some(
                (block) => block.kind === 'tool' && block.callId === event.callId,
              )
                ? { ...message, blocks: reduceBlocks(message.blocks, event) }
                : message,
            ),
          }))
        } else if (
          event.type === 'block.start' ||
          event.type === 'block.delta' ||
          event.type === 'block.end' ||
          event.type === 'tool.start'
        ) {
          replaceBlocks((blocks) => reduceBlocks(blocks, event))
        }
      }

      try {
        await streamAgentRun(targetId, body, handleEvent, controller.signal)
      } catch (reason) {
        if (!controller.signal.aborted) {
          failWith(reason instanceof Error ? reason.message : '运行失败')
        }
      } finally {
        setIsRunning(false)
        abortRef.current = null
        await loadWorkspace(targetId).catch(() => undefined)
      }
    },
    [appendMessage, patchMessage, loadWorkspace],
  )

  function submitMessage(event?: FormEvent) {
    event?.preventDefault()
    if (!activeConversation || isRunning) return
    const text = input.trim()
    if (!text) return

    if (interrupt) {
      if (!interrupt.freeText) return
      appendMessage(activeConversation.id, {
        id: messageId(),
        role: 'user',
        blocks: [{ id: messageId(), kind: 'text', text, done: true }],
      })
      setInput('')
      void runAgent(activeConversation.id, {
        answer:
          interrupt.freeText.wrap === 'respond'
            ? { decisions: [{ type: 'respond', message: text }] }
            : text,
        display: text,
      })
      return
    }

    appendMessage(activeConversation.id, {
      id: messageId(),
      role: 'user',
      blocks: [{ id: messageId(), kind: 'text', text, done: true }],
    })
    setInput('')
    void runAgent(activeConversation.id, { text })
  }

  function startResize(key: PaneKey, event: ReactPointerEvent<HTMLDivElement>) {
    event.preventDefault()
    resizeRef.current = { key, startX: event.clientX, startWidth: paneWidths[key] }
    setDraggingPane(key)
  }

  function openDeleteDialog(conversation: Conversation) {
    setDeleteMode(DEFAULT_DELETE_SCOPE)
    setPendingDelete(conversation)
  }

  async function confirmDeleteConversation() {
    const target = pendingDelete
    if (!target) return
    setIsDeleting(true)
    setError(null)
    try {
      await deleteConversation(target.id, deleteMode)
      setConversations((current) => current.filter((item) => item.id !== target.id))
      setMessagesByConversation((current) => {
        const next = { ...current }
        delete next[target.id]
        return next
      })
      setPendingDelete(null)
      if (activeConversation?.id === target.id) {
        setActiveConversation(null)
        setSnapshot(null)
        setViewSpec({})
        setChangeset(null)
        setInterrupt(null)
        setSelectedNodeId(null)
        setSelectedRelationId(null)
        navigate('/')
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '删除失败')
    } finally {
      setIsDeleting(false)
    }
  }

  function answerInterrupt(option: InterruptOption) {
    if (!activeConversation) return
    appendMessage(activeConversation.id, {
      id: messageId(),
      role: 'user',
      blocks: [{ id: messageId(), kind: 'text', text: option.label, done: true }],
    })
    void runAgent(activeConversation.id, { answer: option.answer, display: option.label })
  }

  return (
    <main
      className={`modeling-page ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}
      style={
        {
          '--conv-pane-w': `${paneWidths.conversations}px`,
          '--chat-pane-w': `${paneWidths.chat}px`,
        } as CSSProperties
      }
    >
      <aside className="conversation-pane">
        <header className="conversation-pane__header">
          <div className="brand brand--compact">
            <span className="brand__mark">
              <Sparkles size={17} />
            </span>
            {!sidebarCollapsed ? (
              <div>
                <strong>OntoWeave</strong>
                <span>建模工作台</span>
              </div>
            ) : null}
          </div>
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={sidebarCollapsed ? '展开会话栏' : '收起会话栏'}
            onClick={() => setSidebarCollapsed((value) => !value)}
          >
            {sidebarCollapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
        </header>

        <button
          type="button"
          className="new-conversation-button"
          onClick={() => void startConversation()}
        >
          <MessageSquarePlus size={17} />
          {!sidebarCollapsed ? '新建对话' : null}
        </button>

        <nav className="conversation-list" aria-label="会话列表">
          {conversations.map((conversation) => (
            <div
              key={conversation.id}
              className={`conversation-list__item ${
                conversation.id === activeConversation?.id ? 'is-active' : ''
              }`}
            >
              <button
                type="button"
                className="conversation-list__open"
                onClick={() => navigate(`/modeling/${conversation.id}`)}
                title={conversation.title || '未命名对话'}
              >
                <span className="conversation-list__dot" />
                {!sidebarCollapsed ? (
                  <span className="conversation-list__title">
                    {conversation.title || '未命名对话'}
                  </span>
                ) : null}
              </button>
              {!sidebarCollapsed ? (
                <button
                  type="button"
                  className="conversation-list__delete"
                  aria-label={`删除对话 ${conversation.title || '未命名对话'}`}
                  title="删除对话"
                  onClick={() => openDeleteDialog(conversation)}
                >
                  <Trash2 size={14} />
                </button>
              ) : null}
            </div>
          ))}
        </nav>
      </aside>

      <div
        className={`pane-resizer pane-resizer--conversations ${
          draggingPane === 'conversations' ? 'is-dragging' : ''
        }`}
        role="separator"
        aria-orientation="vertical"
        aria-label="拖拽调整会话栏宽度"
        onPointerDown={(event) => startResize('conversations', event)}
      />

      <section className="chat-pane">
        <header className="chat-header">
          <div>
            <p className="eyebrow">Conversation</p>
            <div className="chat-title-row">
              <h1>{activeConversation?.title || '选择一个会话'}</h1>
              {activeConversation ? (
                <button
                  type="button"
                  className="icon-button icon-button--quiet"
                  aria-label="重命名会话"
                  onClick={openRenameDialog}
                >
                  <Pencil size={14} />
                </button>
              ) : null}
            </div>
          </div>
          {activeConversation ? (
            <span className={`run-status ${isRunning ? 'is-running' : ''}`}>
              {isRunning ? <LoaderCircle size={14} /> : <CircleCheck size={14} />}
              {isRunning ? '正在整理' : '已同步'}
            </span>
          ) : null}
        </header>

        <div className="message-scroll">
          {!activeConversation ? (
            <div className="chat-empty">
              <Sparkles size={26} />
              <strong>从一个业务故事开始</strong>
              <p>新建对话，或者从左侧继续已有的一张图。</p>
            </div>
          ) : null}

          {messages.map((message) => (
            <article
              key={message.id}
              className={`message-bubble message-bubble--${message.role} ${
                message.kind === 'error' ? 'is-error' : ''
              } ${isNoticeMessage(message.blocks) ? 'is-notice' : ''}`}
            >
              {message.blocks.length === 0 ? <span className="typing-dot" /> : null}
              {message.blocks.map((block) => {
                if (block.kind === 'notice') {
                  return <ChatNotice key={block.id} block={block} />
                }
                if (block.kind === 'reasoning') {
                  return (
                    <ThinkingBlock key={block.id} text={block.text} done={Boolean(block.done)} />
                  )
                }
                if (block.kind === 'tool') {
                  return <ToolCallBlock key={block.id} block={block} />
                }
                if (block.kind === 'interrupt') {
                  const pending = interrupt?.blockId === block.id
                  return (
                    <InterruptCard
                      key={block.id}
                      block={block}
                      pending={pending}
                      disabled={isRunning}
                      onAnswer={answerInterrupt}
                    />
                  )
                }
                if (message.role === 'user') {
                  return (
                    <p key={block.id} className="message-text">
                      {block.text}
                    </p>
                  )
                }
                return <MarkdownText key={block.id} text={block.text} />
              })}
            </article>
          ))}

          <div ref={messagesEndRef} />
        </div>

        {hasPendingOntologyChanges(changeset) ? (
          <ChangesetBar
            summary={opSummary}
            isConfirming={isConfirming}
            onReject={() => void handleReject()}
            onConfirm={() => void confirmCurrentChanges()}
          />
        ) : null}

        <form className="composer" onSubmit={submitMessage}>
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={
              interrupt
                ? interrupt.freeText
                  ? INTERRUPT_PLACEHOLDER.freeText
                  : INTERRUPT_PLACEHOLDER.optionsOnly
                : '描述业务规则、对象或关系'
            }
            rows={3}
            disabled={!activeConversation}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                submitMessage()
              }
            }}
          />
          <div className="composer__footer">
            <span>{isRunning ? '智能体正在处理' : 'Enter 发送 · Shift + Enter 换行'}</span>
            <div className="composer__actions">
              {isRunning ? (
                <button
                  type="button"
                  className="icon-button"
                  aria-label="停止"
                  onClick={() => abortRef.current?.abort()}
                >
                  <Square size={15} />
                </button>
              ) : null}
              <button
                type="submit"
                className="send-button"
                disabled={
                  !activeConversation ||
                  !input.trim() ||
                  isRunning ||
                  Boolean(interrupt && !interrupt.freeText)
                }
              >
                <Send size={16} />
                发送
              </button>
            </div>
          </div>
        </form>

        {error ? (
          <div className="error-toast">
            <CircleAlert size={16} />
            <span>{error}</span>
            <button type="button" aria-label="关闭错误" onClick={() => setError(null)}>
              <X size={14} />
            </button>
          </div>
        ) : null}
      </section>

      <div
        className={`pane-resizer pane-resizer--chat ${
          draggingPane === 'chat' ? 'is-dragging' : ''
        }`}
        role="separator"
        aria-orientation="vertical"
        aria-label="拖拽调整对话栏宽度"
        onPointerDown={(event) => startResize('chat', event)}
      />

      <section className="graph-pane">
        <OntologyCanvas
          snapshot={snapshot}
          viewSpec={viewSpec}
          changeset={changeset}
          selectedNodeId={selectedNodeId}
          selectedRelationId={selectedRelationId}
          onSelectNode={handleSelectNode}
          onSelectRelation={handleSelectRelation}
          onLayoutChange={handleLayoutChange}
          onMoveNode={handleMoveNode}
          onSaveView={() => persistView(viewSpec)}
        />
        {selectedNode ? (
          <NodeDrawer
            key={`${selectedNode.id}:${nodeSavedToken}`}
            node={selectedNode}
            pendingDeleteIds={pendingDeleteIds}
            isSaving={isSavingNode}
            error={nodeSaveError}
            onClose={closeDrawer}
            onSubmit={(draft, options) => void saveSelectedNode(draft, options)}
          />
        ) : null}
        {selectedRelation && selectedRelationEndpoints ? (
          <RelationDrawer
            key={`${selectedRelation.id}:${relationSavedToken}`}
            relation={selectedRelation}
            sourceName={selectedRelationEndpoints.source}
            targetName={selectedRelationEndpoints.target}
            foreignKeyCandidates={selectedRelationForeignKeyCandidates}
            isSaving={isSavingRelation}
            error={relationSaveError}
            onClose={closeRelationDrawer}
            onSubmit={(draft, options) => void saveSelectedRelation(draft, options)}
            onDelete={() => void deleteSelectedRelation()}
          />
        ) : null}
      </section>

      {pendingDelete ? (
        <DeleteConversationDialog
          conversationTitle={pendingDelete.title}
          scope={deleteMode}
          isDeleting={isDeleting}
          onScopeChange={setDeleteMode}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDeleteConversation()}
        />
      ) : null}

      {renameOpen ? (
        <RenameConversationDialog
          title={draftConversationTitle}
          isRenaming={isRenaming}
          onTitleChange={setDraftConversationTitle}
          onCancel={() => setRenameOpen(false)}
          onSubmit={() => void submitRename()}
        />
      ) : null}

    </main>
  )
}
