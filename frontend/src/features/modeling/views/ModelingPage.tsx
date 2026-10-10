import {
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  CircleX,
  LoaderCircle,
  MessageSquarePlus,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Send,
  Sparkles,
  Square,
  X,
} from 'lucide-react'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  confirmChangeset,
  createConversation,
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
import { OntologyCanvas } from '../components/OntologyCanvas'
import { MarkdownText } from '../components/MarkdownText'
import { ThinkingBlock } from '../components/ThinkingBlock'
import { ToolCallBlock } from '../components/ToolCallBlock'
import {
  blocksFromMessage,
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
  GraphSnapshot,
  InterruptOption,
  MessageBlock,
  ViewSpec,
} from '../../../shared/types'

function messageId() {
  return crypto.randomUUID()
}

function formatOpSummary(changeset: Changeset | null) {
  if (!changeset) return []
  const counts = changeset.ops.reduce<Record<string, number>>((result, op) => {
    result[op.op] = (result[op.op] ?? 0) + 1
    return result
  }, {})
  return [
    counts.create ? `新增 ${counts.create}` : null,
    counts.update ? `修改 ${counts.update}` : null,
    counts.delete ? `删除 ${counts.delete}` : null,
  ].filter((item): item is string => Boolean(item))
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
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [renameOpen, setRenameOpen] = useState(false)
  const [draftConversationTitle, setDraftConversationTitle] = useState('')
  const [isRenaming, setIsRenaming] = useState(false)
  const [isConfirming, setIsConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const saveTimerRef = useRef<number | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  const messages = useMemo(
    () =>
      activeConversation
        ? (messagesByConversation[activeConversation.id] ?? [])
        : [],
    [activeConversation, messagesByConversation],
  )
  const selectedNode =
    snapshot?.nodes.find((node) => node.id === selectedNodeId) ?? null
  const opSummary = useMemo(() => formatOpSummary(changeset), [changeset])

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

  const loadWorkspace = useCallback(async (targetId: string) => {
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
    setSelectedNodeId(null)
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
  }, [])

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
    <main className={`modeling-page ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
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
            <button
              type="button"
              key={conversation.id}
              className={conversation.id === activeConversation?.id ? 'is-active' : ''}
              onClick={() => navigate(`/modeling/${conversation.id}`)}
              title={conversation.title || '未命名对话'}
            >
              <span className="conversation-list__dot" />
              {!sidebarCollapsed ? (
                <span>{conversation.title || '未命名对话'}</span>
              ) : null}
              {!sidebarCollapsed ? <ChevronRight size={14} /> : null}
            </button>
          ))}
        </nav>
      </aside>

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
              }`}
            >
              {message.blocks.length === 0 ? <span className="typing-dot" /> : null}
              {message.blocks.map((block) => {
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
                    <section
                      key={block.id}
                      className={`interrupt-card ${pending ? 'is-pending' : ''}`}
                    >
                      <div className="interrupt-card__question">
                        <CircleAlert size={15} />
                        <span>{block.question}</span>
                      </div>
                      {block.options.length ? (
                        <div className="interrupt-options">
                          {block.options.map((option) => (
                            <button
                              type="button"
                              key={option.label}
                              onClick={() => answerInterrupt(option)}
                              disabled={!pending || isRunning}
                            >
                              {option.label}
                            </button>
                          ))}
                        </div>
                      ) : null}
                      {pending ? (
                        <p className="interrupt-card__hint">
                          {block.freeText ? '也可以直接在下方输入回答' : '请点击上面的选项回答'}
                        </p>
                      ) : null}
                    </section>
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

        {changeset?.status === 'open' ? (
          <section className="changeset-bar">
            <div>
              <span className="changeset-bar__icon">
                <Sparkles size={16} />
              </span>
              <div>
                <strong>待确认改动</strong>
                <span>
                  {opSummary.length ? opSummary.join(' · ') : '视图已更新'}
                </span>
              </div>
            </div>
            <div className="changeset-bar__actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => void handleReject()}
              >
                <CircleX size={15} />
                撤销
              </button>
              <button
                type="button"
                className="primary-button"
                onClick={() => void confirmCurrentChanges()}
                disabled={isConfirming}
              >
                {isConfirming ? <LoaderCircle size={15} /> : <CircleCheck size={15} />}
                {isConfirming ? '确认中' : '确认'}
              </button>
            </div>
          </section>
        ) : null}

        <form className="composer" onSubmit={submitMessage}>
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={
              interrupt
                ? interrupt.freeText
                  ? '回答上面的问题'
                  : '请点击上面的选项回答'
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

      <section className="graph-pane">
        <OntologyCanvas
          snapshot={snapshot}
          viewSpec={viewSpec}
          changeset={changeset}
          selectedNodeId={selectedNodeId}
          onSelectNode={setSelectedNodeId}
          onLayoutChange={handleLayoutChange}
          onMoveNode={handleMoveNode}
          onSaveView={() => persistView(viewSpec)}
        />
        {selectedNode ? (
          <aside className="node-inspector">
            <header>
              <div>
                <span className={`status-dot status-dot--${selectedNode.status}`}>
                  {selectedNode.status === 'draft' ? <Sparkles size={12} /> : <CircleCheck size={12} />}
                  {selectedNode.status === 'draft' ? '草稿' : '已确认'}
                </span>
                <strong>{selectedNode.name}</strong>
              </div>
              <button
                type="button"
                className="icon-button icon-button--quiet"
                aria-label="关闭详情"
                onClick={() => setSelectedNodeId(null)}
              >
                <X size={15} />
              </button>
            </header>
            <p>{selectedNode.description || '尚未补充描述'}</p>
            <div className="property-list">
              {selectedNode.properties.length ? (
                selectedNode.properties.map((property) => (
                  <div key={property.id}>
                    <span>{property.name}</span>
                    <code>{property.datatype}</code>
                    {property.required ? <em>必填</em> : null}
                  </div>
                ))
              ) : (
                <span className="empty-list">暂无属性</span>
              )}
            </div>
          </aside>
        ) : null}
      </section>

      {renameOpen ? (
        <div className="dialog-backdrop" role="presentation">
          <section className="view-dialog" role="dialog" aria-modal="true">
            <header>
              <div>
                <span className="view-dialog__icon">
                  <Pencil size={20} />
                </span>
                <div>
                  <strong>重命名会话</strong>
                  <span>会话列表和图名称会同步更新</span>
                </div>
              </div>
              <button
                type="button"
                className="icon-button icon-button--quiet"
                aria-label="关闭"
                onClick={() => setRenameOpen(false)}
              >
                <X size={16} />
              </button>
            </header>
            <label>
              会话名称
              <input
                value={draftConversationTitle}
                onChange={(event) => setDraftConversationTitle(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    void submitRename()
                  }
                }}
                autoFocus
              />
            </label>
            <footer>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setRenameOpen(false)}
              >
                <ChevronLeft size={15} />
                取消
              </button>
              <button
                type="button"
                className="primary-button"
                onClick={() => void submitRename()}
                disabled={!draftConversationTitle.trim() || isRenaming}
              >
                {isRenaming ? <LoaderCircle size={15} /> : <Pencil size={15} />}
                {isRenaming ? '保存中' : '保存'}
              </button>
            </footer>
          </section>
        </div>
      ) : null}
    </main>
  )
}
