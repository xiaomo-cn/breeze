import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Typography, Button, Tag, Card, Modal, Select, message, Space, Progress, Empty } from 'antd';
import { PlusOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { getSprint, addTaskToSprint, removeTaskFromSprint } from '../api/sprints';
import { listTasks, createTask } from '../api/tasks';
import { useKanbanStore } from '../stores/kanbanStore';
import KanbanBoard from '../components/kanban/KanbanBoard';
import TaskDetailDrawer from '../components/task/TaskDetailDrawer';
import type { Task, Sprint } from '../types';
import BurndownChart from '../components/sprint/BurndownChart';
import SchedulingPanel from '../components/sprint/SchedulingPanel';

const { Title, Text } = Typography;

const statusLabels: Record<string, string> = { planning: '规划中', active: '进行中', closed: '已关闭' };
const statusColors: Record<string, string> = { planning: 'blue', active: 'green', closed: 'default' };

export default function SprintDetailPage() {
  const { id, sprintId } = useParams<{ id: string; sprintId: string }>();
  const projectId = Number(id);
  const sid = Number(sprintId);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedTaskId = searchParams.get('taskId');

  const [sprint, setSprint] = useState<Sprint | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [backlogTasks, setBacklogTasks] = useState<Task[]>([]);
  const [selectedTaskIds, setSelectedTaskIds] = useState<number[]>([]);
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [quickTitle, setQuickTitle] = useState('');

  const loadBoard = useKanbanStore((s) => s.loadBoard);
  const boardLoading = useKanbanStore((s) => s.loading);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const sprintData = await getSprint(projectId, sid);
      setSprint(sprintData);
      await loadBoard(projectId, sid);
    } catch {
      message.error('加载数据失败');
    } finally {
      setLoading(false);
    }
  }, [projectId, sid, loadBoard]);

  useEffect(() => { loadData(); }, [loadData]);

  // 从 store 获取最新任务列表（用于燃尽图和进度计算）
  useEffect(() => {
    const cols = useKanbanStore.getState().columns;
    setTasks(cols.flatMap((c) => c.tasks));
  });

  const handleRemoveTask = async (taskId: number) => {
    Modal.confirm({
      title: '移除任务',
      content: '将此任务从 Sprint 中移除？',
      onOk: async () => {
        await removeTaskFromSprint(projectId, sid, taskId);
        message.success('任务已移除');
        useKanbanStore.getState().removeTask(taskId);
      },
    });
  };

  const handleAddTasks = async () => {
    if (selectedTaskIds.length === 0) return;
    try {
      for (const tid of selectedTaskIds) {
        await addTaskToSprint(projectId, sid, tid);
      }
      message.success(`已添加 ${selectedTaskIds.length} 个任务`);
      setAddModalOpen(false);
      setSelectedTaskIds([]);
      loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '添加失败');
    }
  };

  const openAddModal = async () => {
    try {
      const result = await listTasks(projectId, { size: 200 });
      setBacklogTasks((result.items || []).filter((t) => !t.sprintId && t.status !== 'done'));
      setAddModalOpen(true);
    } catch {
      message.error('加载 Backlog 失败');
    }
  };

  const handleQuickCreate = async () => {
    if (!quickTitle.trim()) return;
    try {
      await createTask(projectId, { title: quickTitle.trim(), sprintId: sid });
      message.success('任务创建成功');
      setQuickCreateOpen(false);
      setQuickTitle('');
      loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '创建失败');
    }
  };

  // Sprint 内任务点击：在当前页面打开详情侧边栏
  const handleTaskClick = (taskId: number) => {
    setSearchParams({ taskId: String(taskId) });
  };

  const handleTaskClose = () => {
    setSearchParams({});
  };

  if (!sprint && !loading) {
    return <Empty description="Sprint 不存在" />;
  }

  if (loading) {
    return <div style={{ padding: 24, textAlign: 'center' }}>加载中...</div>;
  }

  const completedCount = tasks.filter((t) => t.status === 'done').length;
  const totalCount = tasks.length;
  const progressPercent = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  return (
    <div style={{ padding: 24 }}>
      {/* Header */}
      <div style={{ marginBottom: 16 }}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(`/projects/${projectId}/sprints`)}
          style={{ marginBottom: 12 }}
        >
          返回
        </Button>
        {sprint && (
          <div>
            <Space>
              <Title level={4} style={{ margin: 0, color: '#1e3a5f' }}>{sprint.name}</Title>
              <Tag color={statusColors[sprint.status]}>{statusLabels[sprint.status]}</Tag>
            </Space>
            {sprint.goal && (
              <div>
                <Text type="secondary">{sprint.goal}</Text>
              </div>
            )}
            <div style={{ marginTop: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {sprint.startDate} ~ {sprint.endDate}
              </Text>
            </div>
            <div style={{ marginTop: 12, maxWidth: 400 }}>
              <Progress
                percent={progressPercent}
                format={() => `${completedCount}/${totalCount} 任务`}
              />
            </div>
          </div>
        )}
      </div>

      {/* Kanban Board — 统一复用 KanbanBoard 组件 */}
      <KanbanBoard
        projectId={projectId}
        loading={boardLoading}
        onTaskClick={handleTaskClick}
        filter={{ sprintId: sid }}
        onTaskRemove={handleRemoveTask}
      />

      {/* Actions */}
      <div style={{ marginBottom: 24, marginTop: 16, display: 'flex', gap: 12 }}>
        {sprint && sprint.status !== 'closed' && (
          <>
            <Button type="primary" onClick={openAddModal}>
              从 Backlog 添加任务
            </Button>
            <Button onClick={() => setQuickCreateOpen(true)}>
              快速创建任务
            </Button>
          </>
        )}
        <SchedulingPanel sprintId={sid} />
      </div>

      {/* Burndown */}
      <Card title="燃尽图" style={{ marginTop: 16, background: 'rgba(255,255,255,0.8)', borderRadius: 10, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}>
        <BurndownChart projectId={projectId} sprintId={sid} />
      </Card>

      {/* Add from Backlog Modal */}
      <Modal
        title="从 Backlog 添加任务"
        open={addModalOpen}
        onOk={handleAddTasks}
        onCancel={() => {
          setAddModalOpen(false);
          setSelectedTaskIds([]);
        }}
        okText="添加"
        cancelText="取消"
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择任务..."
          value={selectedTaskIds}
          onChange={setSelectedTaskIds}
          options={backlogTasks.map((t) => ({
            label: `${t.key || ''} ${t.title}`,
            value: t.id,
          }))}
        />
      </Modal>

      {/* Quick Create Modal */}
      <Modal
        title="快速创建任务"
        open={quickCreateOpen}
        onOk={handleQuickCreate}
        onCancel={() => {
          setQuickCreateOpen(false);
          setQuickTitle('');
        }}
        okText="创建"
        cancelText="取消"
      >
        <input
          type="text"
          placeholder="任务标题"
          value={quickTitle}
          onChange={(e) => setQuickTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleQuickCreate();
          }}
          style={{
            width: '100%',
            padding: '8px 12px',
            borderRadius: 6,
            border: '1px solid #d9d9d9',
            fontSize: 14,
          }}
        />
      </Modal>

      {/* 任务详情侧边栏 */}
      <TaskDetailDrawer
        taskId={selectedTaskId ? Number(selectedTaskId) : null}
        projectId={projectId}
        open={!!selectedTaskId}
        onClose={handleTaskClose}
        onUpdated={() => loadBoard(projectId, sid)}
      />
    </div>
  );
}
