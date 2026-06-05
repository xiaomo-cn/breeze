export interface AiChatRequest {
  projectId: number;
  message: string;
  conversationId?: number;
}

/** 工具事件结构 */
export interface ToolEvent {
  type: 'tool_start' | 'tool_end' | 'tool_confirmation';
  toolName: string;
  message: string;
  timestamp: number;
  /** 确认用参数（仅 tool_confirmation 事件） */
  pendingId?: string;
  confirmDetails?: string;
}

export type StreamStatus = 'connecting' | 'streaming' | 'reconnecting' | 'disconnected' | 'done';

export interface StreamCallbacks {
  onChunk: (text: string) => void;
  onToolEvent: (event: ToolEvent) => void;
  onDone: () => void;
  onError: (err: unknown) => void;
  onStatusChange?: (status: StreamStatus) => void;
  signal?: AbortSignal;
}

/** 重连配置 */
const RECONNECT_BASE_DELAY = 2000;   // 初始 2 秒
const RECONNECT_MAX_DELAY = 30000;   // 最大 30 秒
const RECONNECT_MAX_ATTEMPTS = 3;

export async function streamChat(
  request: AiChatRequest,
  callbacks: StreamCallbacks,
  attempt = 0,
): Promise<void> {
  const { onChunk, onToolEvent, onDone, onError, onStatusChange, signal } = callbacks;
  const token = localStorage.getItem('accessToken');

  try {
    onStatusChange?.(attempt > 0 ? 'reconnecting' : 'connecting');

    const response = await fetch('/api/v1/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
      signal,
    });

    if (!response.ok) {
      throw new Error(`AI chat failed: ${response.status}`);
    }

    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error('No response body');
    }

    onStatusChange?.('streaming');
    const decoder = new TextDecoder();
    let buffer = '';
    let currentEvent = '';

    while (true) {
      // 检查中止信号
      if (signal?.aborted) {
        reader.cancel();
        return;
      }

      let done: boolean;
      let value: Uint8Array | undefined;
      try {
        const result = await reader.read();
        done = result.done;
        value = result.value;
      } catch (readErr) {
        // 读取中断 — 可能是网络问题，尝试重连
        if (attempt < RECONNECT_MAX_ATTEMPTS && !signal?.aborted) {
          await delay(RECONNECT_BASE_DELAY * Math.pow(2, attempt));
          return streamChat(request, callbacks, attempt + 1);
        }
        onStatusChange?.('disconnected');
        onError(readErr);
        return;
      }

      if (done || !value) {
        onStatusChange?.('done');
        onDone();
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (!data) continue;

          if (currentEvent === 'tool_start' || currentEvent === 'tool_end' || currentEvent === 'tool_confirmation') {
            try {
              const parsed: ToolEvent = JSON.parse(data);
              parsed.type = currentEvent;
              onToolEvent(parsed);
            } catch {
              onChunk(data);
            }
          } else {
            onChunk(data);
          }
          currentEvent = '';
        }
      }
    }
  } catch (err: any) {
    if (err.name === 'AbortError') return;
    if (attempt < RECONNECT_MAX_ATTEMPTS && !signal?.aborted) {
      await delay(RECONNECT_BASE_DELAY * Math.pow(2, attempt));
      return streamChat(request, callbacks, attempt + 1);
    }
    onStatusChange?.('disconnected');
    onError(err);
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export interface Conversation {
  id: number;
  title: string;
  model: string;
  messageCount?: number;
  lastMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AiMessage {
  id: number;
  conversationId: number;
  role: string;
  content: string;
  createdAt: string;
}

export async function listConversations(projectId: number): Promise<Conversation[]> {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(`/api/v1/ai/conversations?projectId=${projectId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error('Failed to list conversations');
  return response.json();
}

export async function deleteConversation(id: number): Promise<void> {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(`/api/v1/ai/conversations/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error('Failed to delete conversation');
}

export async function getMessages(id: number): Promise<AiMessage[]> {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(`/api/v1/ai/conversations/${id}/messages`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error('Failed to get messages');
  return response.json();
}

/**
 * AI 任务拆解 — 流式获取子任务 JSON。
 */
export async function streamBreakdown(
  taskId: number,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: unknown) => void,
): Promise<void> {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(`/api/v1/ai/breakdown/${taskId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    throw new Error(`Breakdown failed: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('No response body');
  }

  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      onDone();
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const text = line.slice(5).trim();
        if (text) {
          onChunk(text);
        }
      }
    }
  }
}

/**
 * 子任务节点结构（仅一级，不嵌套）。
 */
export interface SubtaskNode {
  title: string;
  type?: string;
  priority?: string;
  estimatedHours?: number;
}

/**
 * 确认拆解结果，批量创建子任务。
 */
/**
 * 自然语言查询 — 安全执行 AI 生成的 SQL。
 */
export async function executeNlQuery(
  sql: string,
): Promise<{ columns: string[]; rows: Record<string, unknown>[]; total: number }> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch('/api/v1/ai/nl-query/execute', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ sql }),
  });
  if (!res.ok) throw new Error('NL query execution failed');
  return res.json();
}

export async function confirmBreakdown(taskId: number, subtasks: SubtaskNode[]): Promise<any> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/breakdown/${taskId}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(subtasks),
  });
  if (!res.ok) {
    throw new Error(`Confirm breakdown failed: ${res.status}`);
  }
  return res.json();
}

/** 确认待执行的工具操作 */
export async function confirmTool(pendingId: string): Promise<{ status: string; result: string }> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/confirm-tool/${pendingId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed to confirm tool');
  return res.json();
}

/** 拒绝待执行的工具操作 */
export async function rejectTool(pendingId: string): Promise<{ status: string }> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/reject-tool/${pendingId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed to reject tool');
  return res.json();
}
