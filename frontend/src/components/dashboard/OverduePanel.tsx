import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, List, Typography, Space, Tag, Spin, Empty } from 'antd';
import { ClockCircleOutlined } from '@ant-design/icons';
import { getMyOverdueTasks, type MyOverdueTasks, type TaskSummary } from '../../api/tasks';

const { Text } = Typography;

const statusLabels: Record<string, string> = {
  todo: '待办',
  in_progress: '进行中',
  review: '评审中',
};
const priorityColors: Record<string, string> = {
  urgent: 'red',
  high: 'orange',
  medium: 'blue',
  low: 'default',
};

/** 计算逾期天数 */
function daysOverdue(dueDate: string): number {
  const due = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  due.setHours(0, 0, 0, 0);
  return Math.floor((today.getTime() - due.getTime()) / (1000 * 60 * 60 * 24));
}

function Section({ title, items, color, showDays }: {
  title: string;
  items: TaskSummary[];
  color: string;
  showDays?: boolean;
}) {
  const navigate = useNavigate();
  if (items.length === 0) return null;

  return (
    <div style={{ marginBottom: 12 }}>
      <Text strong style={{ color, fontSize: 13 }}>{title} ({items.length})</Text>
      <List
        size="small"
        dataSource={items}
        renderItem={(item) => (
          <List.Item
            style={{ padding: '4px 0', cursor: 'pointer', fontSize: 12 }}
            onClick={() => navigate(`/projects/${item.projectId}?taskId=${item.id}`)}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
              <Text style={{ fontSize: 12 }}>{item.key}</Text>
              <Text style={{ flex: 1, fontSize: 12 }} ellipsis>{item.title}</Text>
              {item.status && (
                <Tag style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>
                  {statusLabels[item.status] || item.status}
                </Tag>
              )}
              {item.priority && item.priority !== 'medium' && (
                <Tag color={priorityColors[item.priority]} style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>
                  {item.priority}
                </Tag>
              )}
              {showDays && (
                <Text type="danger" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>
                  逾期 {daysOverdue(item.dueDate)} 天
                </Text>
              )}
            </div>
          </List.Item>
        )}
      />
    </div>
  );
}

export default function OverduePanel() {
  const [data, setData] = useState<MyOverdueTasks | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getMyOverdueTasks()
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin style={{ display: 'block', margin: '20px auto' }} />;

  const total = data ? data.overdue.length + data.dueToday.length + data.dueSoon.length : 0;
  if (total === 0) {
    return (
      <Card
        title={<Space><ClockCircleOutlined />逾期任务</Space>}
        size="small"
        style={{ marginBottom: 24 }}
      >
        <Empty description="暂无逾期任务" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </Card>
    );
  }

  return (
    <Card
      title={<Space><ClockCircleOutlined />逾期任务</Space>}
      size="small"
      style={{ marginBottom: 24 }}
    >
      <Section title="已逾期" items={data!.overdue} color="#ef4444" showDays />
      <Section title="今天到期" items={data!.dueToday} color="#f97316" />
      <Section title="3天内到期" items={data!.dueSoon} color="#eab308" />
    </Card>
  );
}
