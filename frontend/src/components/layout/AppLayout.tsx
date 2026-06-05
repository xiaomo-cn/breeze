import { useState, useEffect, Component } from 'react';
import { Outlet } from 'react-router-dom';
import { Layout, theme, Result, Button } from 'antd';
import Sidebar from './Sidebar';
import Header from './Header';
import AiChatPanel from '../ai/AiChatPanel';
import { useRealtimeEvents } from '../../hooks/useRealtimeEvents';
import { useNotificationStore } from '../../stores/notificationStore';
import { useKanbanStore } from '../../stores/kanbanStore';
import type { Task } from '../../types';

const { Content } = Layout;

class ErrorBoundary extends Component<
  { children: React.ReactNode },
  { hasError: boolean; error: Error | null }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="页面出错了"
          subTitle={this.state.error?.message}
          extra={
            <Button
              type="primary"
              onClick={() => this.setState({ hasError: false, error: null })}
            >
              重试
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const { token } = theme.useToken();

  const { subscribe } = useRealtimeEvents();
  const { incrementUnread, prependNotification } = useNotificationStore();
  const upsertTask = useKanbanStore((s) => s.upsertTask);
  const removeTask = useKanbanStore((s) => s.removeTask);

  useEffect(() => {
    const unsubNotif = subscribe('notification', (_event, data: any) => {
      incrementUnread();
      prependNotification({
        id: data.id ?? 0,
        userId: 0,
        type: data.type,
        title: data.title,
        referenceType: data.referenceType,
        referenceId: data.referenceId,
        isRead: false,
        createdAt: data.createdAt,
      });
    });

    const unsubTask = subscribe('task_updated', (_event, data: any) => {
      if (data && data.id) {
        upsertTask(data as Task);
      }
    });

    const unsubTaskDel = subscribe('task_deleted', (_event, data: any) => {
      if (data && data.id) {
        removeTask(data.id);
      }
    });

    return () => {
      unsubNotif();
      unsubTask();
      unsubTaskDel();
    };
  }, [subscribe, incrementUnread, prependNotification, upsertTask, removeTask]);

  return (
    <Layout style={{ minHeight: '100vh', background: 'transparent' }}>
      <Header collapsed={collapsed} onToggle={() => setCollapsed(!collapsed)} />
      <Layout style={{ background: 'transparent' }}>
        <Sidebar collapsed={collapsed} onCollapse={setCollapsed} />
        <Content
          className="app-content"
          style={{
            margin: 16,
            padding: 24,
            background: 'transparent',
            borderRadius: token.borderRadiusLG,
            minHeight: 280,
            overflow: 'hidden',
            position: 'relative',
            zIndex: 1,
          }}
        >
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </Content>
      </Layout>
      <AiChatPanel />
    </Layout>
  );
}
