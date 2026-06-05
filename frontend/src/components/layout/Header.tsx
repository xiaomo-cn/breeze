import { Layout, Button, Dropdown, Space, Breadcrumb, Avatar, Badge, Popover, Tag } from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  BellOutlined,
  HomeOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useLocation, useParams, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../stores/authStore';
import { useNotificationStore } from '../../stores/notificationStore';
import { listPositions } from '../../api/admin';
import client from '../../api/client';
import type { Position } from '../../types';
import NotificationDropdown from '../notification/NotificationDropdown';

const { Header: AntHeader } = Layout;

interface Props {
  collapsed: boolean;
  onToggle: () => void;
}

export default function Header({ collapsed, onToggle }: Props) {
  const { username, logout } = useAuthStore();
  const { unreadCount, fetchUnreadCount } = useNotificationStore();
  const navigate = useNavigate();
  const [positionTag, setPositionTag] = useState<{ name: string; color: string } | null>(null);

  // 获取当前用户的职务
  useEffect(() => {
    Promise.all([
      client.get('/users/me'),
      listPositions().catch(() => [] as Position[]),
    ]).then(([userRes, posList]) => {
      const positionId = userRes.data?.positionId;
      if (positionId) {
        const pos = posList.find((p: Position) => p.id === positionId);
        if (pos) setPositionTag({ name: pos.name, color: pos.color });
      }
    }).catch(() => {
      // 静默失败
    });
  }, []);

  useEffect(() => {
    fetchUnreadCount();
  }, [fetchUnreadCount]);

  const location = useLocation();
  const { id: projectId } = useParams<{ id: string }>();

  const isProjectPage = location.pathname.includes('/projects/');
  const isSettingsPage = location.pathname.includes('/settings');

  const items = {
    items: [
      { key: 'username', label: username, disabled: true },
      { type: 'divider' as const },
      {
        key: 'profile',
        icon: <SettingOutlined />,
        label: '个人设置',
        onClick: () => navigate('/profile'),
      },
      { type: 'divider' as const },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: () => logout(),
      },
    ],
  };

  return (
    <AntHeader
      style={{
        background: 'rgba(255,255,255,0.75)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderBottom: '1px solid rgba(59,130,246,0.12)',
        position: 'relative',
        zIndex: 10,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <span style={{
          fontWeight: 800,
          fontSize: 18,
          letterSpacing: '0.5px',
          whiteSpace: 'nowrap',
          background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          backgroundClip: 'text',
          textShadow: '0 1px 2px rgba(59,130,246,0.15)',
        }}>
          Breeze
        </span>
        <Button
          type="text"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={onToggle}
        />
        <Breadcrumb
          className="header-breadcrumb"
          items={[
            { title: <HomeOutlined />, path: '/' },
            ...(isProjectPage
              ? [{ title: projectId ? `项目 #${projectId}` : '项目', path: `/projects/${projectId}` }]
              : []),
            ...(isSettingsPage ? [{ title: '设置' }] : []),
          ]}
        />
      </div>
      <Space size="middle">
        <Popover
          content={<NotificationDropdown />}
          trigger="click"
          placement="bottomRight"
        >
          <Badge count={unreadCount} size="small" offset={[-2, 2]}>
            <BellOutlined style={{ fontSize: 16, cursor: 'pointer' }} />
          </Badge>
        </Popover>
        <Dropdown menu={items} placement="bottomRight">
          <Space style={{ cursor: 'pointer' }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', lineHeight: 1.2 }}>
              <span>{username}</span>
              {positionTag && (
                <Tag color={positionTag.color} style={{ fontSize: 10, lineHeight: '16px', margin: 0, padding: '0 4px' }}>
                  {positionTag.name}
                </Tag>
              )}
            </div>
          </Space>
        </Dropdown>
      </Space>
    </AntHeader>
  );
}
