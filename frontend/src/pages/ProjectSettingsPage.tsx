import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Typography, Tabs, Spin, Button } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { getProject } from '../api/projects';
import ProjectEditForm from '../components/project/ProjectEditForm';
import MemberList from '../components/project/MemberList';
import BoardSettings from '../components/board/BoardSettings';
import type { Project } from '../types';

const { Title } = Typography;

export default function ProjectSettingsPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const [project, setProject] = useState<Project | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    setProject(null);
    getProject(projectId).then(setProject);
  }, [projectId]);

  const handleUpdated = () => {
    getProject(projectId).then(setProject);
  };

  if (!project) return <Spin style={{ display: 'block', margin: '40px auto' }} />;

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/projects/${projectId}`)}>
          返回看板
        </Button>
        <Title level={4} style={{ margin: 0, color: '#1e3a5f' }}>{project.name} 设置</Title>
      </div>
      <Tabs
        items={[
          {
            key: 'general',
            label: '基本信息',
            children: <ProjectEditForm project={project} onUpdated={handleUpdated} />,
          },
          {
            key: 'members',
            label: '成员管理',
            children: <MemberList projectId={projectId} ownerId={project.ownerId} />,
          },
          {
            key: 'board',
            label: '看板设置',
            children: <BoardSettings projectId={projectId} />,
          },
        ]}
      />
    </div>
  );
}
