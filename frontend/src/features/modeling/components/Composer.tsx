import { Send, Square } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useModeling } from '../context/modelingContext'
import { INTERRUPT_PLACEHOLDER } from '../lib/interruptCopy'

/** 输入区：一句话发出去；停在中断上时，这句话就是那次中断的回答。 */
export function Composer() {
  const { workspace, agent } = useModeling()
  const [input, setInput] = useState('')

  const conversation = workspace.conversation
  const interrupt = agent.interrupt
  const waitingForFreeText = Boolean(interrupt?.freeText)

  function submit(event?: FormEvent) {
    event?.preventDefault()
    if (!conversation || agent.isRunning) return
    const text = input.trim()
    if (!text) return
    setInput('')
    void agent.send(conversation.id, text)
  }

  const placeholder = interrupt
    ? waitingForFreeText
      ? INTERRUPT_PLACEHOLDER.freeText
      : INTERRUPT_PLACEHOLDER.optionsOnly
    : '描述业务规则、对象或关系'

  return (
    <form className="composer" onSubmit={submit}>
      <textarea
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder={placeholder}
        rows={3}
        disabled={!conversation}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            submit()
          }
        }}
      />
      <div className="composer__footer">
        <span>{agent.isRunning ? '智能体正在处理' : 'Enter 发送 · Shift + Enter 换行'}</span>
        <div className="composer__actions">
          {agent.isRunning ? (
            <button type="button" className="icon-button" aria-label="停止" onClick={agent.abort}>
              <Square size={15} />
            </button>
          ) : null}
          <button
            type="submit"
            className="send-button"
            disabled={
              !conversation || !input.trim() || agent.isRunning || Boolean(interrupt && !waitingForFreeText)
            }
          >
            <Send size={16} />
            发送
          </button>
        </div>
      </div>
    </form>
  )
}
