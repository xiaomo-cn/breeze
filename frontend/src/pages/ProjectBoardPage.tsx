import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { Typography, Spin, Space, Button } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useKanbanStore } from '../stores/kanbanStore';
import KanbanBoard from '../components/kanban/KanbanBoard';
import TaskCreateModal from '../components/kanban/TaskCreateModal';
import TaskDetailDrawer from '../components/task/TaskDetailDrawer';
import RiskPanel from '../components/risk/RiskPanel';
import { getProject } from '../api/projects';
import type { Project } from '../types';

const { Title } = Typography;

export default function ProjectBoardPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const [project, setProject] = useState<Project | null>(null);
  const loadBoard = useKanbanStore((s) => s.loadBoard);
  const loadTasks = useKanbanStore((s) => s.loadTasks);
  const loading = useKanbanStore((s) => s.loading);
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedTaskId = searchParams.get('taskId');
  const navigate = useNavigate();

  useEffect(() => {
    getProject(projectId).then(setProject);
    loadBoard(projectId);
  }, [projectId, loadBoard]);

  const handleTaskClick = (taskId: number) => {
    setSearchParams({ taskId: String(taskId) });
  };

  const handleTaskClose = () => {
    setSearchParams({});
  };

  if (!project) return <Spin style={{ display: 'block', margin: '40px auto' }} />;

  return (
    <div>
      <div className="flex justify-between items-start mb-4 page-header">
        <Title level={4} style={{ margin: 0 }}>{project.name}</Title>
        <Space align="start">
          <TaskCreateModal projectId={projectId} />
          <Button
            icon={<SettingOutlined />}
            onClick={() => navigate(`/projects/${projectId}/settings`)}
          >
            设置
          </Button>
        </Space>
      </div>
      <KanbanBoard
        projectId={projectId}
        loading={loading}
        onTaskClick={handleTaskClick}
      />
      <div style={{ marginTop: 24 }}>
        <RiskPanel projectId={projectId} />
      </div>
      <TaskDetailDrawer
        taskId={selectedTaskId ? Number(selectedTaskId) : null}
        projectId={projectId}
        open={!!selectedTaskId}
        onClose={handleTaskClose}
        onUpdated={() => loadTasks(projectId)}
      />
    </div>
  );
}
