import { List, Badge, Typography, Button, Empty, Spin } from 'antd';
import {
  UserSwitchOutlined,
  MessageOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotificationStore } from '../../stores/notificationStore';
import { markRead, markAllRead } from '../../api/notifications';

const typeIcon: Record<string, React.ReactNode> = {
  TASK_ASSIGNED: <UserSwitchOutlined style={{ color: '#1677ff' }} />,
  TASK_CREATED: <MessageOutlined style={{ color: '#1677ff' }} />,
  COMMENT_ADDED: <MessageOutlined style={{ color: '#52c41a' }} />,
  MENTIONED: <span style={{ color: '#fa8c16', fontWeight: 'bold' }}>@</span>,
  COLLABORATOR_ADDED: <TeamOutlined style={{ color: '#722ed1' }} />,
};

export default function NotificationDropdown() {
  const { recentList, loading, fetchRecentList, markRead: markReadStore, markAllRead: markAllReadStore } =
    useNotificationStore();
  const navigate = useNavigate();

  useEffect(() => {
    fetchRecentList();
  }, [fetchRecentList]);

  const handleClick = async (item: (typeof recentList)[0]) => {
    if (!item.isRead) {
      await markRead(item.id);
      markReadStore(item.id);
    }
    if (item.referenceType === 'task' && item.referenceId) {
      navigate(`/projects/0?taskId=${item.referenceId}`);
    }
  };

  const handleMarkAllRead = async () => {
    await markAllRead();
    markAllReadStore();
  };

  return (
    <div style={{ width: 360, maxHeight: 420, overflow: 'hidden' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '8px 12px',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <Typography.Text strong>通知</Typography.Text>
        <Button type="link" size="small" onClick={handleMarkAllRead}>
          全部已读
        </Button>
      </div>
      <div style={{ maxHeight: 360, overflow: 'auto' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : recentList.length === 0 ? (
          <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <List
            dataSource={recentList}
            renderItem={(item) => (
              <List.Item
                onClick={() => handleClick(item)}
                style={{
                  cursor: 'pointer',
                  padding: '8px 12px',
                  background: item.isRead ? 'transparent' : '#f6f8fa',
                }}
              >
                <List.Item.Meta
                  avatar={
                    <span style={{ fontSize: 18 }}>
                      {typeIcon[item.type] || <MessageOutlined />}
                    </span>
                  }
                  title={
                    <span style={{ fontSize: 13 }}>
                      {!item.isRead && (
                        <Badge status="processing" style={{ marginRight: 4 }} />
                      )}
                      {item.title}
                    </span>
                  }
                  description={
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {formatRelativeTime(item.createdAt)}
                    </Typography.Text>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </div>
    </div>
  );
}

function formatRelativeTime(dateStr: string | undefined): string {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} 小时前`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay} 天前`;
  return date.toLocaleDateString('zh-CN');
}
