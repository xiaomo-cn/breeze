import { Avatar, Typography, Button, Space, Popconfirm } from 'antd';
import { UserOutlined, DeleteOutlined } from '@ant-design/icons';
import DOMPurify from 'dompurify';
import type { Comment } from '../../types';
import { useAuthStore } from '../../stores/authStore';

interface Props {
  comment: Comment;
  onReply: (parentId: number) => void;
  onDelete: (id: number) => void;
  isReply?: boolean;
}

/** 将文本格式 @提及 转为 HTML span */
function convertMentionsToHtml(text: string): string {
  return text.replace(
    /\[@([^\]]+)\]\(\/users\/(\d+)\)/g,
    '<span style="color:#1677ff;font-weight:500" data-mention-user-id="$2">@$1</span>',
  );
}

/** 判断内容是否为 HTML 格式 */
function isHtml(content: string): boolean {
  return /^<[a-z][\s\S]*>/i.test(content.trim());
}

function renderContent(content: string) {
  // 新评论：TipTap HTML
  if (isHtml(content)) {
    const withMentions = convertMentionsToHtml(content);
    const clean = DOMPurify.sanitize(withMentions, {
      ALLOWED_TAGS: [
        'p', 'strong', 'em', 's', 'u',
        'ul', 'ol', 'li',
        'pre', 'code',
        'blockquote',
        'a', 'img',
        'span', 'br', 'mark',
      ],
      ALLOWED_ATTR: [
        'style', 'data-mention-user-id',
        'href', 'target', 'rel',
        'src', 'alt', 'class',
      ],
    });
    return (
      <div
        className="comment-html-content"
        dangerouslySetInnerHTML={{ __html: clean }}
      />
    );
  }
  // 旧评论：纯文本 + @提及 解析
  const parts = content.split(/(\[@[^\]]+\]\(\/users\/\d+\))/g);
  return (
    <div style={{ whiteSpace: 'pre-wrap' }}>
      {parts.map((part, i) => {
        const match = part.match(/\[@([^\]]+)\]\(\/users\/(\d+)\)/);
        if (match) {
          return (
            <span key={i} style={{ color: '#1677ff', fontWeight: 500 }}>
              @{match[1]}
            </span>
          );
        }
        return <span key={i}>{part}</span>;
      })}
    </div>
  );
}

export default function CommentItem({ comment, onReply, onDelete, isReply }: Props) {
  const userId = useAuthStore((s) => s.userId);
  const displayName = comment.displayName || comment.username || 'Unknown';

  return (
    <div
      style={{
        marginLeft: isReply ? 24 : 0,
        marginBottom: 8,
        borderLeft: isReply ? '2px solid #52c41a' : '2px solid #1677ff',
        paddingLeft: 12,
        paddingTop: 4,
        paddingBottom: 4,
        background: isReply ? '#fafafa' : 'transparent',
        borderRadius: '0 6px 6px 0',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <Avatar size="small" icon={<UserOutlined />} src={comment.avatarUrl} />
        <Typography.Text strong style={{ fontSize: 13 }}>
          {displayName}
        </Typography.Text>
        {isReply && (
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            回复
          </Typography.Text>
        )}
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {formatRelativeTime(comment.createdAt)}
        </Typography.Text>
      </div>
      <div style={{ fontSize: 13, lineHeight: 1.6 }}>
        {renderContent(comment.content)}
      </div>
      <Space size="small" style={{ marginTop: 4 }}>
        {!isReply && (
          <Button type="link" size="small" onClick={() => onReply(comment.id)}>
            回复
          </Button>
        )}
        {comment.userId === userId && (
          <Popconfirm title="确定删除此评论？" onConfirm={() => onDelete(comment.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        )}
      </Space>
    </div>
  );
}

function formatRelativeTime(dateStr?: string): string {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} 小时前`;
  return date.toLocaleDateString('zh-CN');
}
