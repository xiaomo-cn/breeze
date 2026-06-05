import { useDroppable } from '@dnd-kit/core';
import { Typography, Badge } from 'antd';
import type { Task } from '../../types';

const { Text } = Typography;

export interface KanbanColumnData {
  id: string;
  title: string;
  color: string;
  tasks: Task[];
}

interface Props {
  column: KanbanColumnData;
  wipLimit?: number;
  children: React.ReactNode;
}

export default function KanbanColumn({ column, wipLimit, children }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: column.id });

  const limit = (wipLimit && wipLimit > 0) ? wipLimit : undefined;
  const overLimit = limit != null && column.tasks.length > limit;

  return (
    <div
      ref={setNodeRef}
      style={{
        minHeight: 100,
        background: isOver ? 'rgba(59,130,246,0.08)' : undefined,
        borderRadius: 8,
        transition: 'background 0.2s',
      }}
    >
      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Text strong style={{ color: '#1e293b' }}>{column.title}</Text>
        <Badge
          count={column.tasks.length}
          style={{
            backgroundColor: overLimit ? '#ef4444' : '#3b82f6',
            boxShadow: overLimit ? '0 2px 6px rgba(239,68,68,0.3)' : '0 2px 6px rgba(59,130,246,0.3)',
          }}
          overflowCount={999}
        />
        {limit != null && (
          <Text type="secondary" style={{ fontSize: 11 }}>
            / {limit}
          </Text>
        )}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {children}
      </div>
    </div>
  );
}
