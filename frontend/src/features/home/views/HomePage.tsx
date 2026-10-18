import { ArrowRight, Clock3, Database, Network, Plus } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createConversation, listConversations } from '../../../shared/api'
import { CONVERSATION_KIND_MODELING, type Conversation } from '../../../shared/types'

export function HomePage() {
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    void listConversations(CONVERSATION_KIND_MODELING)
      .then(setConversations)
      .catch(() => setConversations([]))
  }, [])

  async function openNewConversation() {
    setCreating(true)
    try {
      const conversation = await createConversation('新对话')
      navigate(`/modeling/${conversation.id}`)
    } finally {
      setCreating(false)
    }
  }

  return (
    <main className="home-page">
      <header className="home-header">
        <div className="brand">
          <span className="brand__mark">
            <Network size={20} />
          </span>
          <div>
            <strong>OntoWeave</strong>
            <span>企业本体建模台</span>
          </div>
        </div>
      </header>

      <section className="home-content">
        <div className="home-intro">
          <p className="eyebrow">Workspace</p>
          <h1>把业务语言织成企业本体</h1>
          <p className="home-intro__copy">
            从一段对话开始，抽取对象、属性和关系。每条改动都先进入草图，再交给团队确认。
          </p>
        </div>

        <button
          type="button"
          className="feature-card"
          onClick={() => void openNewConversation()}
          disabled={creating}
        >
          <span className="feature-card__icon">
            <Network size={30} />
          </span>
          <span className="feature-card__body">
            <strong>建模</strong>
            <span>新建对话，开始梳理业务结构</span>
          </span>
          <span className="feature-card__action">
            {creating ? '创建中' : '进入'}
            <ArrowRight size={17} />
          </span>
        </button>

        <button
          type="button"
          className="feature-card"
          onClick={() => navigate('/data-connections')}
        >
          <span className="feature-card__icon">
            <Database size={30} />
          </span>
          <span className="feature-card__body">
            <strong>数据连接</strong>
            <span>接入 MySQL，选择数据表并持续同步到数据集</span>
          </span>
          <span className="feature-card__action">
            进入
            <ArrowRight size={17} />
          </span>
        </button>

        <section className="recent-section">
          <div className="section-heading">
            <div>
              <Clock3 size={17} />
              <h2>最近对话</h2>
            </div>
            <button
              type="button"
              className="text-button"
              onClick={() => void openNewConversation()}
              disabled={creating}
            >
              <Plus size={15} />
              新建
            </button>
          </div>
          {conversations.length ? (
            <div className="recent-list">
              {conversations.slice(0, 6).map((conversation) => (
                <button
                  type="button"
                  className="recent-item"
                  key={conversation.id}
                  onClick={() => navigate(`/modeling/${conversation.id}`)}
                >
                  <span>{conversation.title || '未命名对话'}</span>
                  <ArrowRight size={15} />
                </button>
              ))}
            </div>
          ) : (
            <div className="empty-list">还没有会话</div>
          )}
        </section>
      </section>
    </main>
  )
}
