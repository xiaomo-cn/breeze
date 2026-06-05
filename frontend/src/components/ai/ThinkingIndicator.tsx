import type { StreamStatus, ToolEvent } from '../../api/ai';

interface ToolCallState {
  toolName: string;
  status: 'running' | 'done' | 'error' | 'confirming';
  message: string;
  timestamp: number;
}

interface ThinkingIndicatorProps {
  /** 流连接状态 */
  streamStatus: StreamStatus;
  /** 最后一条助手消息的工具调用列表 */
  toolCalls?: ToolCallState[];
  /** AI 是否已开始产出内容 */
  hasContent: boolean;
}

/**
 * 思考进度指示器。
 *
 * 在用户发送消息后、AI 开始返回内容前显示，给用户清晰的等待反馈。
 * 有三种状态：
 * 1. connecting — "正在分析你的问题"
 * 2. 有 running 状态工具 — 显示工具执行信息
 * 3. 有内容产出 — 不显示（让内容自然展示）
 */
export default function ThinkingIndicator({
  streamStatus,
  toolCalls,
  hasContent,
}: ThinkingIndicatorProps) {
  // 已有内容产出时不显示
  if (hasContent) return null;

  // 未在流式传输中不显示
  if (streamStatus !== 'connecting' && streamStatus !== 'streaming') return null;

  const runningTool = toolCalls?.find(tc => tc.status === 'running');

  // 工具执行中
  if (runningTool) {
    return (
      <div className="ai-chat-thinking ai-chat-thinking--tool">
        <span>🔧</span>
        <span>{runningTool.message}</span>
        <span className="ai-chat-typing-dots">
          <span /><span /><span />
        </span>
      </div>
    );
  }

  // 连接中 / 等待首个响应
  return (
    <div className="ai-chat-thinking">
      <span>🤔</span>
      <span>正在分析你的问题</span>
      <span className="ai-chat-typing-dots">
        <span /><span /><span />
      </span>
    </div>
  );
}
