import { Drawer, Descriptions, Tag, Button, Popconfirm, Space, Skeleton, Result, message, Tabs, Divider } from 'antd';
import { EditOutlined, DeleteOutlined, RobotOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useTask } from '../../hooks/useTask';
import { updateTask, deleteTask } from '../../api/tasks';
import { getUser } from '../../api/users';
import { getMyProjectRole } from '../../api/projects';
import { streamBreakdown } from '../../api/ai';
import { PRIORITY_COLORS } from '../../constants';
import { useKanbanStore } from '../../stores/kanbanStore';
import { useState, useEffect } from 'react';
import TaskEditForm from './TaskEditForm';
import CommentList from '../comment/CommentList';
import AttachmentList from '../attachment/AttachmentList';
import BreakdownPreview from './BreakdownPreview';
import SubtaskList from './SubtaskList';
import type { Task } from '../../types';

interface Props {
  taskId: number | null;
  projectId: number;
  open: boolean;
  onClose: () => void;
  onUpdated: () => void;
}

/** 判断当前项目角色是否有删除权限（admin 或 manager） */
function canDelete(role: string): boolean {
  return role === 'admin' || role === 'manager';
}

/** 判断当前项目角色是否有编辑权限（非 viewer） */
function canEdit(role: string): boolean {
  return role !== 'viewer' && role !== 'non_member';
}

export default function TaskDetailDrawer({ taskId, projectId, open, onClose, onUpdated }: Props) {
  const [currentTaskId, setCurrentTaskId] = useState<number | null>(null);
  const effectiveTaskId = currentTaskId ?? taskId;

  const { task, loading, error, refetch } = useTask(
    open ? effectiveTaskId : null
  );
  const columnConfigs = useKanbanStore((s) => s.columnConfigs);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [breakdownLoading, setBreakdownLoading] = useState(false);
  const [breakdownJson, setBreakdownJson] = useState('');
  const [breakdownOpen, setBreakdownOpen] = useState(false);
  const [assigneeName, setAssigneeName] = useState<string | null>(null);
  const [collaboratorNames, setCollaboratorNames] = useState<{ id: number; name: string }[]>([]);
  const [myRole, setMyRole] = useState<string>('non_member');

  // 打开时加载当前用户的项目角色
  useEffect(() => {
    if (open && projectId) {
      getMyProjectRole(projectId)
        .then(setMyRole)
        .catch(() => setMyRole('non_member'));
    }
  }, [open, projectId]);

  // 当 props.taskId 变化时（打开新任务），重置内部导航
  useEffect(() => {
    if (open && taskId) {
      setCurrentTaskId(null);
    }
  }, [open, taskId]);

  // 加载负责人名称
  useEffect(() => {
    if (task?.assigneeId) {
      getUser(task.assigneeId)
        .then((user) => setAssigneeName(user.displayName || user.username))
        .catch(() => setAssigneeName(null));
    } else {
      setAssigneeName(null);
    }
  }, [task?.assigneeId]);

  // 加载协作人名称
  useEffect(() => {
    if (task?.collaboratorIds?.length) {
      Promise.all(task.collaboratorIds.map((id) =>
        getUser(id).then((u) => ({ id, name: u.displayName || u.username })).catch(() => ({ id, name: `用户 #${id}` }))
      )).then(setCollaboratorNames);
    } else {
      setCollaboratorNames([]);
    }
  }, [task?.collaboratorIds]);

  const handleSave = async (values: Partial<Task>) => {
    if (!task) return;
    setSaving(true);
    try {
      await updateTask(task.id, values);
      message.success('任务已更新');
      setEditing(false);
      refetch();
      onUpdated();
    } catch (err: unknown) {
      if ((err as { response?: { status?: number } })?.response?.status !== 403) {
        message.error('更新任务失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!task) return;
    try {
      await deleteTask(task.id);
      message.success('任务已删除');
      onClose();
      onUpdated();
    } catch (err: unknown) {
      if ((err as { response?: { status?: number } })?.response?.status !== 403) {
        message.error('删除任务失败');
      }
    }
  };

  // 抽屉关闭时不显示加载/错误状态
  if (!open) {
    return <Drawer open={false} onClose={onClose} width={640} />;
  }

  if (loading) {
    return (
      <Drawer title="任务详情" open={open} onClose={onClose} width={640}>
        <Skeleton active />
      </Drawer>
    );
  }

  if (error || !task) {
    return (
      <Drawer title="任务详情" open={open} onClose={onClose} width={640}>
        <Result status="error" title="加载失败" subTitle={error || '任务未找到'} />
      </Drawer>
    );
  }

  return (
    <Drawer
      title={
        <Space>
          {currentTaskId != null && (
            <Button
              type="text"
              size="small"
              onClick={() => setCurrentTaskId(null)}
              style={{ padding: 0 }}
            >
              ← 返回
            </Button>
          )}
          {task.parentId != null && (
            <Tag color="purple" style={{ fontSize: 11, lineHeight: '18px' }}>子任务</Tag>
          )}
          <span style={{ color: '#999' }}>{task.key}</span>
          <span>{editing ? '编辑任务' : task.title}</span>
        </Space>
      }
      open={open}
      onClose={() => {
        setEditing(false);
        setCurrentTaskId(null);
        onClose();
      }}
      width={640}
      extra={
        !editing && (
          <Space>
            {task.parentId == null && canEdit(myRole) && (
              <Button
                icon={<RobotOutlined />}
                onClick={async () => {
                  if (!task) return;
                  setBreakdownLoading(true);
                  setBreakdownJson('');
                  let text = '';
                  try {
                    await streamBreakdown(
                      task.id,
                      (chunk) => {
                        text += chunk;
                      },
                      () => {
                        setBreakdownJson(text);
                        setBreakdownOpen(true);
                        setBreakdownLoading(false);
                      },
                      () => {
                        message.error('AI 拆解失败');
                        setBreakdownLoading(false);
                      },
                    );
                  } catch {
                    message.error('AI 拆解失败');
                    setBreakdownLoading(false);
                  }
                }}
                loading={breakdownLoading}
              >
                AI 拆解
              </Button>
            )}
            {canEdit(myRole) && (
              <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                编辑
              </Button>
            )}
            {canDelete(myRole) && (
              <Popconfirm title="确定删除此任务？" onConfirm={handleDelete}>
                <Button danger icon={<DeleteOutlined />} />
              </Popconfirm>
            )}
          </Space>
        )
      }
    >
      {editing ? (
        <TaskEditForm
          task={task}
          onSave={handleSave}
          onCancel={() => setEditing(false)}
          saving={saving}
          statusOptions={columnConfigs.map((c) => ({
            value: c.statusMapping,
            label: c.name,
          }))}
        />
      ) : (
        <div>
          {/* 详情区域 */}
          <Descriptions column={2} size="small" bordered style={{ marginBottom: 0 }}>
            <Descriptions.Item label="状态">
              <Tag>{task.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="优先级">
              <Tag color={PRIORITY_COLORS[task.priority] || 'default'}>{task.priority}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag>{task.type}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="负责人">
              {assigneeName ?? (task.assigneeId ? `用户 #${task.assigneeId}` : '-')}
            </Descriptions.Item>
            <Descriptions.Item label="故事点">
              {task.storyPoints ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="截止日期">
              {task.dueDate ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="排序">
              {task.sortOrder}
            </Descriptions.Item>
            {collaboratorNames.length > 0 && (
              <Descriptions.Item label="协作人" span={2}>
                <Space size={[4, 4]} wrap>
                  {collaboratorNames.map((c) => (
                    <Tag key={c.id}>{c.name}</Tag>
                  ))}
                </Space>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="描述" span={2}>
              <div style={{ whiteSpace: 'pre-wrap' }}>{task.description || '暂无描述'}</div>
            </Descriptions.Item>
          </Descriptions>

          {/* 子任务区域 — 仅顶层任务显示 */}
          {task.parentId == null && (
            <>
              <Divider orientation="left" style={{ fontSize: 13, margin: '12px 0 8px' }}>
                <Space size={4}>
                  <UnorderedListOutlined />
                  <span>子任务</span>
                </Space>
              </Divider>
              <SubtaskList
                taskId={task.id}
                projectId={projectId}
                onNavigate={(subId) => setCurrentTaskId(subId)}
              />
            </>
          )}

          {/* 评论 & 附件 */}
          <Divider style={{ margin: '12px 0 8px' }} />
          <Tabs
            items={[
              {
                key: 'comments',
                label: '评论',
                children: <CommentList taskId={task.id} />,
              },
              {
                key: 'attachments',
                label: '附件',
                children: <AttachmentList taskId={task.id} />,
              },
            ]}
          />
        </div>
      )}
      <BreakdownPreview
        taskId={task.id}
        jsonText={breakdownJson}
        open={breakdownOpen}
        onClose={() => setBreakdownOpen(false)}
        onCreated={() => {
          refetch();
          onUpdated();
        }}
      />
    </Drawer>
  );
}
