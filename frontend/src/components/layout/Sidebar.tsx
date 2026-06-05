import { useEffect, useState } from 'react';
import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  ProjectOutlined,
  DashboardOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  BarChartOutlined,
  ScheduleOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { listProjects } from '../../api/projects';
import { useAuthStore } from '../../stores/authStore';
import { useProjectStore } from '../../stores/projectStore';
import type { Project } from '../../types';

const { Sider } = Layout;

interface Props {
  collapsed: boolean;
  onCollapse: (v: boolean) => void;
}

export default function Sidebar({ collapsed, onCollapse }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const currentProjectId = id || location.pathname.match(/\/projects\/(\d+)/)?.[1];
  const role = useAuthStore((s) => s.role);
  const refreshKey = useProjectStore((s) => s.refreshKey);

  useEffect(() => {
    listProjects().then((data) => setProjects(data.items || [])).catch(() => {});
  }, [refreshKey]);

  const baseItems = [
    {
      key: '/',
      icon: <DashboardOutlined style={{ color: '#3b82f6' }} />,
      label: <span style={{ fontWeight: 600, color: '#3b82f6' }}>仪表盘</span>,
    },
    ...(role === 'system_admin'
      ? [{
          key: '/admin/users',
          icon: <TeamOutlined style={{ color: '#8b5cf6' }} />,
          label: <span style={{ fontWeight: 600, color: '#8b5cf6' }}>用户管理</span>,
        }]
      : []),
  ];

  const projectItems = projects.map((p) => {
    const children = [
      {
        key: `/projects/${p.id}`,
        label: '看板',
      },
      {
        key: `/projects/${p.id}/sprints`,
        label: 'Sprint',
        icon: <ThunderboltOutlined />,
      },
      {
        key: `/projects/${p.id}/reports`,
        label: '报表',
        icon: <BarChartOutlined />,
      },
      {
        key: `/projects/${p.id}/gantt`,
        label: '甘特图',
        icon: <ScheduleOutlined />,
      },
      {
        key: `/projects/${p.id}/settings`,
        label: '设置',
        icon: <SettingOutlined />,
      },
    ];

    return {
      key: `project-${p.id}`,
      icon: <ProjectOutlined />,
      label: p.name,
      children,
    };
  });

  const items = [...baseItems, ...projectItems];

  const getSelectedKeys = () => {
    if (currentProjectId && location.pathname.includes('/settings')) {
      return [`/projects/${currentProjectId}/settings`];
    }
    if (currentProjectId && location.pathname.includes('/reports')) {
      return [`/projects/${currentProjectId}/reports`];
    }
    if (currentProjectId && location.pathname.includes('/gantt')) {
      return [`/projects/${currentProjectId}/gantt`];
    }
    if (currentProjectId && location.pathname.includes('/sprints')) {
      return [`/projects/${currentProjectId}/sprints`];
    }
    if (currentProjectId) {
      return [`/projects/${currentProjectId}`];
    }
    return ['/'];
  };

  const getOpenKeys = () => {
    if (currentProjectId) {
      return [`project-${currentProjectId}`];
    }
    return [];
  };

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={onCollapse}
      trigger={null}
      breakpoint="lg"
      collapsedWidth={0}
      width={220}
      style={{
        background: 'rgba(255,255,255,0.65)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        borderRight: '1px solid rgba(59,130,246,0.08)',
      }}
    >
      <Menu
        mode="inline"
        selectedKeys={getSelectedKeys()}
        defaultOpenKeys={getOpenKeys()}
        items={items}
        onClick={({ key }) => navigate(key)}
        style={{
          background: 'transparent',
          borderInlineEnd: 'none',
        }}
      />
    </Sider>
  );
}
