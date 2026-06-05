import { useState, useEffect, useCallback } from 'react';
import { Spin, Empty, message } from 'antd';
import { listComments, createComment, deleteComment } from '../../api/comments';
import CommentItem from './CommentItem';
import CommentEditor from './CommentEditor';
import type { Comment } from '../../types';

interface Props {
  taskId: number;
}

export default function CommentList({ taskId }: Props) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(false);
  const [replyingTo, setReplyingTo] = useState<number | null>(null);

  const loadComments = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listComments(taskId);
      setComments(page.items);
    } catch {
      message.error('加载评论失败');
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    loadComments();
  }, [loadComments]);

  const handleCreate = async (content: string, parentId?: number) => {
    await createComment(taskId, content, parentId);
    setReplyingTo(null);
    loadComments();
  };

  const handleDelete = async (id: number) => {
    await deleteComment(id);
    message.success('评论已删除');
    loadComments();
  };

  if (loading && comments.length === 0) {
    return <Spin style={{ display: 'block', textAlign: 'center', padding: 24 }} />;
  }

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <CommentEditor
          taskId={taskId}
          onSubmit={(html) => handleCreate(html)}
          placeholder="写下你的评论，支持粘贴/拖拽上传图片，输入 @ 提及成员..."
        />
      </div>

      {comments.length === 0 ? (
        <Empty description="暂无评论" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        comments.map((comment) => (
          <div key={comment.id} style={{ marginBottom: 12 }}>
            <CommentItem
              comment={comment}
              onReply={(parentId) => setReplyingTo(parentId)}
              onDelete={handleDelete}
            />
            {comment.replies?.map((reply) => (
              <CommentItem
                key={reply.id}
                comment={reply}
                onReply={() => {}}
                onDelete={handleDelete}
                isReply
              />
            ))}
            {replyingTo === comment.id && (
              <div style={{ marginLeft: 24, marginTop: 8 }}>
                <CommentEditor
                  taskId={taskId}
                  onSubmit={(html) => handleCreate(html, comment.id)}
                  placeholder={`回复 ${comment.displayName || comment.username}...`}
                />
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}
