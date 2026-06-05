import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Input, Card, Typography, Space, message } from 'antd';
import { RobotOutlined, CloseOutlined, SendOutlined, ReloadOutlined, PlusOutlined, MessageOutlined, FullscreenOutlined, FullscreenExitOutlined } from '@ant-design/icons';
import { streamChat, listConversations, deleteConversation, getMessages, type Conversation, type ToolEvent, type StreamStatus } from '../../api/ai';
import MessageBubble, { type ChatMessage } from './MessageBubble';
import ThinkingIndicator from './ThinkingIndicator';
import ConversationSidebar from './ConversationSidebar';
import './aiChat.css';

const { Text } = Typography;

/** 工具调用状态 */
interface ToolCallState {
  toolName: string;
  status: 'running' | 'done' | 'error' | 'confirming';
  message: string;
  timestamp: number;
  pendingId?: string;
  confirmDetails?: string;
}

const SUGGESTED_PROMPTS = [
  '本周我的工作量如何？',
  '显示高优先级任务',
  '为登录页崩溃创建故障报告',
  '总结项目进展',
];

/** 默认尺寸 */
const DEFAULT_SIZE = { width: 500, height: 620 };
/** 最小尺寸 */
const MIN_SIZE = { width: 380, height: 400 };

export default function AiChatPanel() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>('done');
  const [streamError, setStreamError] = useState(false);
  const [conversationId, setConversationId] = useState<number | undefined>();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [showSidebar, setShowSidebar] = useState(false);
  const [maximized, setMaximized] = useState(false);
  const [panelSize, setPanelSize] = useState(DEFAULT_SIZE);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const projectId = location.pathname.match(/\/projects\/(\d+)/)?.[1];

  // 拖拽缩放状态
  const [isResizing, setIsResizing] = useState(false);
  const resizeStartRef = useRef({ x: 0, y: 0, width: 0, height: 0 });

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 拖拽缩放逻辑
  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsResizing(true);
    resizeStartRef.current = {
      x: e.clientX,
      y: e.clientY,
      width: panelSize.width,
      height: panelSize.height,
    };
  }, [panelSize]);

  useEffect(() => {
    if (!isResizing) return;
    const handleMouseMove = (e: MouseEvent) => {
      const dx = e.clientX - resizeStartRef.current.x;
      const dy = e.clientY - resizeStartRef.current.y;
      setPanelSize({
        width: Math.max(MIN_SIZE.width, resizeStartRef.current.width + dx),
        height: Math.max(MIN_SIZE.height, resizeStartRef.current.height + dy),
      });
    };
    const handleMouseUp = () => setIsResizing(false);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing]);

  // 计算实际显示尺寸
  const displaySize = useMemo(() => {
    if (maximized) {
      return {
        width: Math.round(window.innerWidth * 0.92),
        height: Math.round(window.innerHeight * 0.88),
      };
    }
    return panelSize;
  }, [maximized, panelSize]);

  const toggleMaximize = () => setMaximized(prev => !prev);

  const loadConversations = useCallback(async () => {
    if (!projectId) return;
    try {
      const convs = await listConversations(Number(projectId));
      setConversations(convs);
    } catch { /* ignore */ }
  }, [projectId]);

  useEffect(() => {
    if (open) loadConversations();
  }, [open, loadConversations]);

  const loadMessages = async (convId: number) => {
    try {
      const msgs = await getMessages(convId);
      setMessages(msgs.map(m => ({ role: m.role as 'user' | 'assistant', content: m.content })));
    } catch {
      message.error('加载对话失败');
    }
  };

  const appendStream = (chunk: string) => {
    setMessages((prev) => {
      const updated = [...prev];
      const last = updated[updated.length - 1];
      if (last?.role === 'assistant') {
        updated[updated.length - 1] = { ...last, content: last.content + chunk };
      }
      return updated;
    });
  };

  const updateToolCalls = (updater: (prev: ToolCallState[]) => ToolCallState[]) => {
    setMessages((prev) => {
      const updated = [...prev];
      const last = updated[updated.length - 1];
      if (last?.role === 'assistant') {
        updated[updated.length - 1] = {
          ...last,
          toolCalls: updater(last.toolCalls || []),
        };
      }
      return updated;
    });
  };

  const sendMessage = async (text?: string) => {
    const msg = text || input.trim();
    if (!msg || streaming || !projectId) return;

    setInput('');
    setStreamError(false);
    setMessages((prev) => [...prev, { role: 'user', content: msg }]);
    setStreaming(true);
    setMessages((prev) => [...prev, { role: 'assistant', content: '', toolCalls: [] }]);

    const handleToolEvent = (event: ToolEvent) => {
      setMessages((prev) => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last?.role === 'assistant') {
          const existing = last.toolCalls || [];
          if (event.type === 'tool_start') {
            updated[updated.length - 1] = {
              ...last,
              toolCalls: [...existing, {
                toolName: event.toolName,
                status: 'running' as const,
                message: event.message,
                timestamp: event.timestamp,
              }],
            };
          } else if (event.type === 'tool_confirmation') {
            updated[updated.length - 1] = {
              ...last,
              toolCalls: [...existing, {
                toolName: event.toolName,
                status: 'confirming' as const,
                message: event.message,
                timestamp: event.timestamp,
                pendingId: event.pendingId,
                confirmDetails: event.confirmDetails,
              } as any],
            };
          } else if (event.type === 'tool_end') {
            const idx = existing.findIndex(
              t => t.toolName === event.toolName && t.status === 'running'
            );
            const newCalls = [...existing];
            if (idx >= 0) {
              newCalls[idx] = {
                ...newCalls[idx],
                status: event.message.startsWith('❌') ? 'error' as const : 'done' as const,
                message: event.message,
              };
            } else {
              newCalls.push({
                toolName: event.toolName,
                status: event.message.startsWith('❌') ? 'error' as const : 'done' as const,
                message: event.message,
                timestamp: event.timestamp,
              });
            }
            updated[updated.length - 1] = { ...last, toolCalls: newCalls };
          }
        }
        return updated;
      });
    };

    try {
      await streamChat(
        { projectId: Number(projectId), message: msg, conversationId: conversationId },
        {
          onChunk: appendStream,
          onToolEvent: handleToolEvent,
          onStatusChange: (status) => setStreamStatus(status),
          onDone: () => {
            setStreaming(false);
            if (!conversationId) {
              loadConversations().then(() => {
                setConversations(prev => {
                  if (prev.length > 0) setConversationId(prev[0].id);
                  return prev;
                });
              });
            } else {
              loadConversations();
            }
          },
          onError: () => {
            message.error('AI 对话失败');
            setStreamError(true);
            setStreaming(false);
          },
        },
      );
    } catch {
      message.error('AI 对话失败');
      setStreamError(true);
      setStreaming(false);
    }
  };

  const handleNewConversation = () => {
    setMessages([]);
    setConversationId(undefined);
    setStreamError(false);
    setShowSidebar(false);
  };

  const handleSelectConversation = async (conv: Conversation) => {
    setConversationId(conv.id);
    setShowSidebar(false);
    setStreamError(false);
    await loadMessages(conv.id);
  };

  const handleDeleteConversation = async (convId: number) => {
    try {
      await deleteConversation(convId);
      if (conversationId === convId) {
        handleNewConversation();
      }
      loadConversations();
    } catch {
      message.error('删除失败');
    }
  };

  const lastMessage = messages.length > 0 ? messages[messages.length - 1] : null;
  const lastToolCalls = lastMessage?.role === 'assistant' ? (lastMessage.toolCalls as ToolCallState[] | undefined) : undefined;
  const hasContent = lastMessage?.role === 'assistant' ? (lastMessage.content?.length ?? 0) > 0 : false;
  const showThinking = streaming && lastMessage?.role === 'assistant' && !hasContent;

  if (!open) {
    return (
      <Button
        type="primary"
        shape="circle"
        icon={<RobotOutlined />}
        size="large"
        style={{ position: 'fixed', bottom: 24, right: 24, width: 56, height: 56, zIndex: 1000 }}
        onClick={() => setOpen(true)}
      />
    );
  }

  return (
    <div
      style={{
        position: 'fixed',
        bottom: maximized ? '50%' : 24,
        right: maximized ? '50%' : 24,
        transform: maximized ? 'translate(50%, 50%)' : undefined,
        width: displaySize.width,
        height: displaySize.height,
        maxWidth: maximized ? `calc(100vw - 48px)` : undefined,
        maxHeight: maximized ? `calc(100vh - 48px)` : undefined,
        zIndex: 1000,
        display: 'flex',
        flexDirection: 'column',
        userSelect: isResizing ? 'none' : undefined,
      }}
    >
      <Card
        title={
          <Space>
            <RobotOutlined />
            <span>AI 助手</span>
            {conversationId && conversations.find(c => c.id === conversationId) && (
              <Text type="secondary" style={{ fontSize: 12, maxWidth: 120 }} ellipsis>
                {conversations.find(c => c.id === conversationId)?.title}
              </Text>
            )}
          </Space>
        }
        extra={
          <Space>
            <MessageOutlined
              onClick={() => { setShowSidebar(!showSidebar); loadConversations(); }}
              style={{ cursor: 'pointer' }}
              title="对话历史"
            />
            <PlusOutlined onClick={handleNewConversation} style={{ cursor: 'pointer' }} title="新对话" />
            <span
              onClick={toggleMaximize}
              style={{ cursor: 'pointer', fontSize: 14 }}
              title={maximized ? '还原' : '最大化'}
            >
              {maximized ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            </span>
            <CloseOutlined onClick={() => setOpen(false)} style={{ cursor: 'pointer' }} />
          </Space>
        }
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          boxShadow: '0 8px 40px rgba(0,0,0,0.12)',
        }}
        styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: 12 } }}
      >
        {/* 对话历史侧边栏 */}
        {showSidebar && (
          <ConversationSidebar
            conversations={conversations}
            activeId={conversationId}
            onSelect={handleSelectConversation}
            onDelete={handleDeleteConversation}
            onClose={() => setShowSidebar(false)}
          />
        )}

        {/* 连接状态 */}
        {streamStatus === 'reconnecting' && (
          <div style={{ textAlign: 'center', padding: '4px 0', fontSize: 12, color: '#ad6800', background: '#fff7e6', borderRadius: 4, marginBottom: 4 }}>
            🔄 正在重新连接...
          </div>
        )}
        {streamStatus === 'disconnected' && (
          <div style={{ textAlign: 'center', padding: '4px 0', fontSize: 12, color: '#cf1322', background: '#fff1f0', borderRadius: 4, marginBottom: 4 }}>
            ⚠️ 连接中断
            <Button size="small" type="link" onClick={() => sendMessage()} style={{ fontSize: 12, padding: 0 }}>重试</Button>
          </div>
        )}

        {/* 消息区域 */}
        <div style={{ flex: 1, overflowY: 'auto', marginBottom: 12 }}>
          {messages.length === 0 && projectId && (
            <div>
              <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>向我询问项目的任何问题！</Text>
              <Space wrap size={[4, 4]}>
                {SUGGESTED_PROMPTS.map((prompt) => (
                  <Button key={prompt} size="small" style={{ fontSize: 12 }} onClick={() => sendMessage(prompt)}>
                    {prompt}
                  </Button>
                ))}
              </Space>
            </div>
          )}
          {messages.length === 0 && !projectId && (
            <Text type="secondary">打开一个项目以使用 AI 助手。</Text>
          )}

          {messages.map((msg, i) => (
            <MessageBubble
              key={i}
              msg={msg}
              isStreaming={streaming}
              isLastMessage={i === messages.length - 1}
              onUpdateToolCalls={updateToolCalls}
            />
          ))}

          {showThinking && (
            <ThinkingIndicator
              streamStatus={streamStatus}
              toolCalls={lastToolCalls}
              hasContent={hasContent}
            />
          )}

          {streamError && (
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Button size="small" icon={<ReloadOutlined />} onClick={() => sendMessage()}>重试</Button>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* 输入区域 */}
        <Space.Compact style={{ width: '100%' }}>
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={() => sendMessage()}
            placeholder="输入你的消息..."
            disabled={streaming || !projectId}
          />
          <Button type="primary" icon={<SendOutlined />} onClick={() => sendMessage()} loading={streaming} disabled={!projectId} />
        </Space.Compact>
      </Card>

      {/* 右下角拖拽缩放句柄（非最大化时显示） */}
      {!maximized && (
        <div
          onMouseDown={handleResizeStart}
          style={{
            position: 'absolute',
            bottom: 0,
            right: 0,
            width: 16,
            height: 16,
            cursor: 'nwse-resize',
            zIndex: 10,
          }}
          title="拖拽调整大小"
        >
          <svg width="12" height="12" viewBox="0 0 12 12" style={{ position: 'absolute', bottom: 2, right: 2, opacity: 0.4 }}>
            <path d="M0 12 L12 0 M5 12 L12 5 M10 12 L12 10" stroke="#999" strokeWidth="1.5" fill="none" />
          </svg>
        </div>
      )}
    </div>
  );
}
