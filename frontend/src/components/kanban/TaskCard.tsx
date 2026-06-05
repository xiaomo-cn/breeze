import { useDraggable } from '@dnd-kit/core';
import { Card, Tag, Typography, Button, Tooltip } from 'antd';
import { BugOutlined, ThunderboltOutlined, CheckSquareOutlined, DeleteOutlined, UserOutlined, TeamOutlined } from '@ant-design/icons';
import type { Task } from '../../types';
import { PRIORITY_COLORS } from '../../constants';
import { useAuthStore } from '../../stores/authStore';

const { Text } = Typography;

const TYPE_ICONS: Record<string, React.ReactNode> = {
  bug: <BugOutlined style={{ color: '#ff4d4f' }} />,
  story: <ThunderboltOutlined style={{ color: '#52c41a' }} />,
  task: <CheckSquareOutlined style={{ color: '#3b82f6' }} />,
  epic: <ThunderboltOutlined style={{ color: '#722ed1' }} />,
};

interface Props {
  task: Task;
  onClick?: (taskId: number) => void;
  onRemove?: (taskId: number) => void;
  columnColor?: string;
}

export default function TaskCard({ task, onClick, onRemove, columnColor }: Props) {
  const currentUserId = useAuthStore((s) => s.userId);
  const isMine = currentUserId != null && task.assigneeId === currentUserId;
  const isCollaborator = !isMine && currentUserId != null && task.collaboratorIds?.includes(currentUserId);

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: String(task.id),
    data: task,
  });

  const style: React.CSSProperties = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
        opacity: isDragging ? 0.5 : 1,
        cursor: 'grab',
      }
    : { cursor: 'grab' };

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      onClick={() => onClick?.(task.id)}
    >
      <Card
        size="small"
        hoverable
        style={{
          borderRadius: 10,
          boxShadow: columnColor
            ? `0 2px 8px ${columnColor}1a`
            : '0 2px 8px rgba(59,130,246,0.06)',
          border: columnColor
            ? `1px solid ${columnColor}18`
            : '1px solid rgba(59,130,246,0.06)',
          background: columnColor
            ? `linear-gradient(135deg, #fff, ${columnColor}08)`
            : '#fff',
          marginBottom: 8,
        }}
        styles={{ body: { padding: '10px 12px' } }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
          {TYPE_ICONS[task.type] || TYPE_ICONS.task}
          <Text style={{ fontSize: 12, color: '#999' }}>{task.key}</Text>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          {isMine && (
            <Tooltip title="我负责的">
              <UserOutlined style={{ color: '#1677ff', fontSize: 12 }} />
            </Tooltip>
          )}
          {isCollaborator && (
            <Tooltip title="我参与的">
              <TeamOutlined style={{ color: '#722ed1', fontSize: 12 }} />
            </Tooltip>
          )}
          <Text>{task.title}</Text>
        </div>
        <div style={{ marginTop: 4, display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
            <Tag color={PRIORITY_COLORS[task.priority] || 'default'}>
              {task.priority}
            </Tag>
            {task.type && (
              <Tag>{task.type}</Tag>
            )}
            {task.subtaskStats && task.subtaskStats.total > 0 && (
              <Tooltip title={`${task.subtaskStats.total} 个子任务，${task.subtaskStats.done} 个已完成`}>
                <span
                  style={{
                    fontSize: 11,
                    color: task.subtaskStats.done === task.subtaskStats.total ? '#10b981' : '#3b82f6',
                    background: task.subtaskStats.done === task.subtaskStats.total ? '#ecfdf5' : '#eff6ff',
                    padding: '1px 7px',
                    borderRadius: 10,
                    cursor: 'default',
                  }}
                >
                  ✓{task.subtaskStats.done}/{task.subtaskStats.total}
                </span>
              </Tooltip>
            )}
          </div>
          {onRemove && (
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => { e.stopPropagation(); onRemove(task.id); }}
            />
          )}
        </div>
      </Card>
    </div>
  );
}
