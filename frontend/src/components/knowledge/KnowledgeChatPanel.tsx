import { useState, useRef, useEffect } from 'react';
import { Button, Input, App, Dropdown } from 'antd';
import { SendOutlined, HistoryOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import '../ai/aiChat.css';
import { knowledgeChat, fetchConversations, fetchMessages, deleteConversation } from '../../api/knowledge';
import type { KnowledgeConversation, KnowledgeMessage } from '../../types/knowledge';

export default function KnowledgeChatPanel() {
  const [messages, setMessages] = useState<KnowledgeMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<KnowledgeConversation[]>([]);
  const [retryCount, setRetryCount] = useState(0);
  const MAX_RETRIES = 3;
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { message: antMsg } = App.useApp();

  useEffect(() => { loadConversations(); }, []);
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const loadConversations = async () => {
    try { setConversations(await fetchConversations()); }
    catch { /* silent */ }
  };

  const loadConversation = async (id: number) => {
    try {
      const msgs = await fetchMessages(id);
      setMessages(msgs);
      setConversationId(id);
    } catch { antMsg.error('加载对话失败'); }
  };

  const handleNewChat = () => {
    setConversationId(null);
    setMessages([]);
    setRetryCount(0);
  };

  const handleDeleteConv = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await deleteConversation(id);
      antMsg.success('已删除对话');
      if (conversationId === id) handleNewChat();
      loadConversations();
    } catch { antMsg.error('删除失败'); }
  };

  const handleSend = async () => {
    if (!input.trim() || loading) return;
    const userContent = input;
    const userMsg: KnowledgeMessage = {
      id: Date.now(), conversationId: conversationId || 0,
      role: 'user', content: userContent, createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    // 助理占位消息
    const assistantMsg: KnowledgeMessage = {
      id: Date.now() + 1, conversationId: conversationId || 0,
      role: 'assistant', content: '', createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, assistantMsg]);

    await doStreamChat(userContent, 0);
    setLoading(false);
    // 新对话：加载列表获取新创建的对话 ID，再加载消息获取 referencedDocs
    if (!conversationId) {
      try {
        const convs = await fetchConversations();
        if (convs.length > 0) {
          setConversationId(convs[0].id);
          setConversations(convs);
          const msgs = await fetchMessages(convs[0].id);
          if (msgs.length > 0) setMessages(msgs);
        }
      } catch { /* silent */ }
    }
  };

  const doStreamChat = async (userContent: string, attempt: number) => {
    try {
      const response = await knowledgeChat(conversationId, userContent);
      if (!response.ok) throw new Error('请求失败');
      const reader = response.body?.getReader();
      if (!reader) throw new Error('无法读取流');
      const decoder = new TextDecoder();
      let fullContent = '';

      // 累积未处理完的文本片段（跨 chunk 的不完整 SSE 行）
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        // 用 stream:true 确保多字节 UTF-8 字符不丢（暂存不完整字节）
        const text = done
          ? decoder.decode(value, { stream: false })   // 最终 flush
          : decoder.decode(value, { stream: true });

        buffer += text;
        // 按 \n 拆分行，最后一段可能不完整，留在 buffer 中
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data) {
              fullContent += data;
              // 不可变更新：创建新对象避免 React 批处理丢失更新
              setMessages((prev) => {
                const updated = [...prev];
                const idx = updated.length - 1;
                if (idx >= 0 && updated[idx].role === 'assistant') {
                  updated[idx] = { ...updated[idx], content: fullContent };
                }
                return updated;
              });
            }
          }
        }
        if (done) break;
      }

      // 流结束后，从服务端加载最新消息以获取 referencedDocs 和完整内容
      if (conversationId) {
        try {
          const latest = await fetchMessages(conversationId);
          if (latest.length > 0) {
            setMessages(latest);
          }
        } catch { /* 回退：保持当前消息不变 */ }
      }

      setRetryCount(0);
    } catch (err: any) {
      if (attempt < MAX_RETRIES - 1) {
        antMsg.warning(`连接中断，正在重连…（${attempt + 2}/${MAX_RETRIES}）`);
        await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
        await doStreamChat(userContent, attempt + 1);
      } else {
        antMsg.error('连接失败，请稍后重试');
        setMessages((prev) => {
          const updated = [...prev];
          const last = updated[updated.length - 1];
          if (last.role === 'assistant') last.content = '连接中断，请重试';
          return updated;
        });
        setRetryCount(0);
      }
    }
  };

  const formatTime = (ts?: string) => {
    if (!ts) return '';
    const d = new Date(ts);
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
    return `${Math.floor(diff / 86400000)}天前`;
  };

  const conversationMenuItems = conversations.map((conv) => ({
    key: String(conv.id),
    label: (
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: 200 }}>
        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
          {conv.title || '新对话'}
        </span>
        <span style={{ fontSize: 11, color: '#999', marginRight: 8, flexShrink: 0 }}>{formatTime(conv.updatedAt)}</span>
        <DeleteOutlined style={{ fontSize: 12, color: '#999' }} onClick={(e) => handleDeleteConv(conv.id, e as any)} />
      </div>
    ),
    onClick: () => loadConversation(conv.id),
  }));

  return (
    <div style={{ display: 'grid', gridTemplateRows: 'auto 1fr auto', flex: 1, minHeight: 0, background: '#fff' }}>
      {/* 头部 */}
      <div style={{ padding: '12px 20px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', gap: 10, justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>🤖 AI 知识库问答</h3>
          <span style={{ fontSize: 11, padding: '2px 10px', borderRadius: 10, background: '#e6f4ff', color: '#1677ff', border: '1px solid #91caff' }}>
            基于知识库文档
          </span>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {conversations.length > 0 && (
            <Dropdown menu={{ items: conversationMenuItems }} trigger={['click']} placement="bottomRight">
              <Button type="text" size="small" icon={<HistoryOutlined />}>
                历史对话
              </Button>
            </Dropdown>
          )}
          <Button type="text" size="small" icon={<PlusOutlined />} onClick={handleNewChat}>
            新对话
          </Button>
        </div>
      </div>

      {/* 消息区 */}
      <div style={{ minHeight: 0, overflow: 'auto', padding: '16px 20px' }}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 80, color: '#bfbfbf' }}>
            <div style={{ fontSize: 40, marginBottom: 8 }}>💡</div>
            <div style={{ fontSize: 13 }}>我是知识库 AI 助手，基于上传的文档回答问题</div>
            <div style={{ display: 'flex', gap: 6, marginTop: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
              {['公司的 API 接口规范是什么？', '数据库命名有什么要求？', '前端组件库有哪些使用指南？', '项目开发流程是怎样的？'].map((q) => (
                <span key={q} style={{ padding: '5px 12px', borderRadius: 16, fontSize: 12, border: '1px solid #d9d9d9', cursor: 'pointer' }}
                  onClick={() => setInput(q)}>{q}</span>
              ))}
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {messages.map((msg) => (
              <div key={msg.id} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                {msg.role === 'assistant' && msg.referencedDocs && msg.referencedDocs.length > 0 && (
                  <div style={{ display: 'flex', gap: 6, marginBottom: 4, flexWrap: 'wrap' }}>
                    {msg.referencedDocs
                      .filter((ref: any) => ref.score == null || ref.score >= 60)
                      .map((ref: any, i: number) => (
                      <span key={i} style={{ padding: '3px 10px', borderRadius: 6, fontSize: 11, color: '#1677ff', background: '#f0f5ff', border: '1px solid #d6e4ff' }}>
                        {ref.fileType === 'md' ? '📝' : ref.fileType === 'pdf' ? '📄' : '📎'} {ref.title}
                        {ref.score != null && (
                          <span style={{ marginLeft: 4, color: '#999' }}>相关度 {ref.score}%</span>
                        )}
                      </span>
                    ))}
                  </div>
                )}
                <div style={{
                  maxWidth: '75%', padding: '10px 14px', borderRadius: msg.role === 'user' ? '12px 4px 12px 12px' : '4px 12px 12px 12px',
                  background: msg.role === 'user' ? '#1677ff' : '#fff', color: msg.role === 'user' ? '#fff' : 'rgba(0,0,0,.85)',
                  border: msg.role === 'assistant' ? '1px solid #f0f0f0' : 'none',
                  fontSize: 13, lineHeight: 1.65,
                }}>
                  {msg.role === 'user' ? msg.content : (
                    msg.content ? (
                      <div className="ai-chat-markdown">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {msg.content}
                        </ReactMarkdown>
                      </div>
                    ) : (loading ? '思考中...' : '')
                  )}
                </div>
              </div>
            ))}
            {loading && retryCount > 0 && (
              <div style={{ textAlign: 'center', fontSize: 12, color: '#faad14' }}>
                正在重连…（{retryCount}/{MAX_RETRIES}）
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* 输入区 */}
      <div style={{ padding: '12px 20px', borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8 }}>
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (e.shiftKey) return; // Shift+Enter 换行
            e.preventDefault();
            handleSend();
          }}
          placeholder="输入问题，AI 将基于知识库文档回答…（Shift+Enter 换行）"
          autoSize={{ minRows: 2, maxRows: 8 }}
          disabled={loading}
          style={{ flex: 1 }}
        />
        <Button type="primary" icon={<SendOutlined />} onClick={() => handleSend()} loading={loading}>
          发送
        </Button>
      </div>
    </div>
  );
}
