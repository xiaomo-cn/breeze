import { Button, Typography } from 'antd';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useMemo } from 'react';
import { confirmTool, rejectTool } from '../../api/ai';

const { Text } = Typography;

/**
 * 预处理 AI 回复内容，轻量修复 DeepSeek 常见的 Markdown 小毛病。
 *
 * 只做最安全的修复——在行首标记后补空格。
 * 不做任何会破坏已有正确格式的激进替换。
 */
function normalizeMarkdown(raw: string): string {
  let text = raw;

  // 标题：行首 ###标题 → ### 标题（仅补空格，不改结构）
  text = text.replace(/^(#{1,6})([^\s#])/gm, '$1 $2');

  // 无序列表：行首 -文本 → - 文本
  text = text.replace(/^([-*])([^\s-*])/gm, '$1 $2');

  // 有序列表：行首 1.文本 → 1. 文本
  text = text.replace(/^(\d+\.)([^\s])/gm, '$1 $2');

  // 引用：行首 >文本 → > 文本
  text = text.replace(/^(>)([^\s>])/gm, '$1 $2');

  return text;
}

/** 工具调用状态 */
interface ToolCallState {
  toolName: string;
  status: 'running' | 'done' | 'error' | 'confirming';
  message: string;
  timestamp: number;
  pendingId?: string;
  confirmDetails?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCallState[];
}

interface MessageBubbleProps {
  msg: ChatMessage;
  isStreaming: boolean;
  isLastMessage: boolean;
  /** 更新消息的 toolCalls 状态（用于确认/取消操作） */
  onUpdateToolCalls?: (updater: (prev: ToolCallState[]) => ToolCallState[]) => void;
}

/** 状态图标映射 */
const STATUS_ICON: Record<string, string> = {
  running: '🔧',
  confirming: '⏳',
  done: '✅',
  error: '❌',
};

/**
 * 单条聊天消息气泡。
 *
 * - 用户消息：蓝色气泡，纯文本
 * - 助手消息：灰色气泡，Markdown 渲染 + 工具调用状态卡 + 打字动画
 */
export default function MessageBubble({
  msg,
  isStreaming,
  isLastMessage,
  onUpdateToolCalls,
}: MessageBubbleProps) {
  const isUser = msg.role === 'user';
  const showTypingDots = !isUser && isStreaming && isLastMessage && msg.content.length === 0 && (!msg.toolCalls || msg.toolCalls.length === 0);
  const showCursor = !isUser && isStreaming && isLastMessage && msg.content.length > 0;

  // 预处理 AI 返回内容，修复常见 Markdown 格式问题
  const normalizedContent = useMemo(() => {
    if (isUser || !msg.content) return msg.content;
    return normalizeMarkdown(msg.content);
  }, [msg.content, isUser]);

  return (
    <div style={{ marginBottom: 8, textAlign: isUser ? 'right' : 'left' }}>
      {/* 助手消息的工具调用状态卡片 */}
      {!isUser && msg.toolCalls && msg.toolCalls.length > 0 && (
        <div style={{ marginBottom: 4 }}>
          {msg.toolCalls.map((tc, j) => (
            <div
              key={`${tc.toolName}-${tc.timestamp}-${j}`}
              className={`ai-chat-tool-card ai-chat-tool-card--${tc.status}`}
            >
              {STATUS_ICON[tc.status] || '🔧'}
              {' '}{tc.message}
              {tc.status === 'running' && (
                <span className="ai-chat-typing-dots">
                  <span /><span /><span />
                </span>
              )}
              {/* 确认按钮 */}
              {tc.status === 'confirming' && tc.pendingId && (
                <span style={{ marginLeft: 8 }}>
                  <Button
                    size="small" type="primary"
                    style={{ fontSize: 11, height: 22, padding: '0 8px' }}
                    onClick={async (e) => {
                      e.stopPropagation();
                      try {
                        const r = await confirmTool(tc.pendingId!);
                        onUpdateToolCalls?.(prev => prev.map(c =>
                          c.pendingId === tc.pendingId
                            ? { ...c, status: 'done' as const, message: r.result || tc.message }
                            : c
                        ));
                      } catch { /* ignore */ }
                    }}
                  >
                    确认
                  </Button>
                  <Button
                    size="small"
                    style={{ fontSize: 11, height: 22, padding: '0 8px', marginLeft: 4 }}
                    onClick={async (e) => {
                      e.stopPropagation();
                      try {
                        await rejectTool(tc.pendingId!);
                        onUpdateToolCalls?.(prev => prev.map(c =>
                          c.pendingId === tc.pendingId
                            ? { ...c, status: 'error' as const, message: '已取消: ' + tc.message }
                            : c
                        ));
                      } catch { /* ignore */ }
                    }}
                  >
                    取消
                  </Button>
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {/* 消息气泡 */}
      <div className={`ai-chat-bubble ai-chat-bubble--${isUser ? 'user' : 'assistant'}`}>
        {isUser ? (
          // 用户消息：纯文本
          msg.content
        ) : (
          // 助手消息：Markdown 渲染
          <>
            {normalizedContent ? (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  a: ({ href, children }) => (
                    <Typography.Link href={href} target="_blank" rel="noopener noreferrer">
                      {children}
                    </Typography.Link>
                  ),
                  code: ({ className, children, ...props }) => {
                    const isBlock = className?.startsWith('language-');
                    if (isBlock) {
                      return (
                        <pre className="ai-chat-code-block">
                          <code className={className}>{children}</code>
                        </pre>
                      );
                    }
                    return <code {...props}>{children}</code>;
                  },
                }}
              >
                {normalizedContent}
              </ReactMarkdown>
            ) : null}
            {/* 打字动画：无内容时显示跳动圆点 */}
            {showTypingDots && (
              <span className="ai-chat-typing-dots">
                <span /><span /><span />
              </span>
            )}
            {/* 闪烁光标：有内容且正在流式输出 */}
            {showCursor && <span className="ai-chat-cursor" />}
          </>
        )}
      </div>
    </div>
  );
}
