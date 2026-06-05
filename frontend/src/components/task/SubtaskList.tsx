import { useState, useEffect, useCallback } from 'react';
import { List, Tag, Button, Modal, Form, Input, Select, InputNumber, Space, message, Tooltip, Popconfirm } from 'antd';
import { PlusOutlined, CaretRightOutlined, CheckOutlined, UndoOutlined, DeleteOutlined } from '@ant-design/icons';
import { getChildren, createTask, updateTask, deleteTask } from '../../api/tasks';
import UserSelect from '../common/UserSelect';
import type { Task } from '../../types';
import { PRIORITY_COLORS } from '../../constants';

interface Props {
  taskId: number;
  projectId: number;
  isSubtask?: boolean;
  onNavigate?: (taskId: number) => void;
}

const STATUS_LABELS: Record<string, string> = {
  todo: '待开始',
  in_progress: '进行中',
  review: '评审中',
  done: '已完成',
};

const ACTIVE_STATUSES = ['todo', 'in_progress', 'review'];

export default function SubtaskList({ taskId, projectId, isSubtask, onNavigate }: Props) {
  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getChildren(taskId);
      setSubtasks(data);
    } catch {
      message.error('加载子任务失败');
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await createTask(projectId, { ...values, parentId: taskId });
      message.success('子任务已创建');
      form.resetFields();
      setCreateOpen(false);
      load();
    } catch (e: any) {
      // 后端返回的业务错误需要提示
      if (e?.response?.data?.message) {
        message.error(e.response.data.message);
      }
      // 表单校验失败不提示
    } finally {
      setSaving(false);
    }
  };

  const handleStart = async (subtask: Task) => {
    try {
      await updateTask(subtask.id, { status: 'in_progress' });
      message.success(`${subtask.key} 已开始`);
      load();
    } catch {
      message.error('操作失败');
    }
  };

  const handleDone = async (subtask: Task) => {
    try {
      await updateTask(subtask.id, { status: 'done' });
      message.success(`${subtask.key} 已完成`);
      load();
    } catch {
      message.error('操作失败');
    }
  };

  const handleReopen = async (subtask: Task) => {
    try {
      await updateTask(subtask.id, { status: 'todo' });
      message.success(`${subtask.key} 已重新打开`);
      load();
    } catch {
      message.error('操作失败');
    }
  };

  const handleDelete = async (subtask: Task) => {
    try {
      await deleteTask(subtask.id);
      message.success(`${subtask.key} 已删除`);
      load();
    } catch {
      message.error('删除失败');
    }
  };

  const active = subtasks.filter((t) => ACTIVE_STATUSES.includes(t.status));
  const done = subtasks.filter((t) => t.status === 'done');

  const renderRow = (subtask: Task) => (
    <List.Item
      key={subtask.id}
      onClick={() => onNavigate?.(subtask.id)}
      style={{ cursor: 'pointer', padding: '8px 12px', borderRadius: 6 }}
      onMouseEnter={(e) => (e.currentTarget.style.background = '#fafafa')}
      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
        <Tooltip title="查看详情">
          <CaretRightOutlined
            style={{ color: '#bbb', fontSize: 11, flexShrink: 0 }}
            onClick={(e) => {
              e.stopPropagation();
              onNavigate?.(subtask.id);
            }}
          />
        </Tooltip>
        <span style={{ color: '#8c8c8c', fontSize: 12, flexShrink: 0, width: 55 }}>
          {subtask.key}
        </span>
        <span
          style={{
            flex: 1,
            fontSize: 13,
            textDecoration: subtask.status === 'done' ? 'line-through' : 'none',
            color: subtask.status === 'done' ? '#bbb' : 'inherit',
          }}
        >
          {subtask.title}
        </span>
        <Tag style={{ fontSize: 11, margin: 0 }}>{subtask.type || 'subtask'}</Tag>
        <Tag
          color={
            subtask.status === 'done'
              ? 'green'
              : subtask.status === 'in_progress'
                ? 'orange'
                : 'blue'
          }
          style={{ fontSize: 11, margin: 0 }}
        >
          {STATUS_LABELS[subtask.status] || subtask.status}
        </Tag>
        <Tag color={PRIORITY_COLORS[subtask.priority] || 'default'} style={{ fontSize: 11, margin: 0 }}>
          {subtask.priority}
        </Tag>
        {subtask.estimatedHours != null && (
          <span style={{ color: '#8c8c8c', fontSize: 12, flexShrink: 0, width: 36, textAlign: 'right' }}>
            {subtask.estimatedHours}h
          </span>
        )}
        <ActionButtons
          subtask={subtask}
          onStart={handleStart}
          onDone={handleDone}
          onReopen={handleReopen}
          onDelete={handleDelete}
        />
      </div>
    </List.Item>
  );

  return (
    <div>
      {!isSubtask && (
        <div style={{ marginBottom: 8 }}>
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            block
            onClick={() => setCreateOpen(true)}
          >
            添加子任务
          </Button>
        </div>
      )}

      <List
        loading={loading}
        dataSource={[]}
        split={false}
        locale={{ emptyText: '暂无子任务' }}
      >
        {/* 空状态由 List 自身处理，下面手动渲染分组列表 */}
      </List>

      {active.length > 0 && (
        <>
          <div style={{ fontSize: 11, color: '#8c8c8c', padding: '8px 12px 4px', textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 500 }}>
            进行中
          </div>
          {active.map(renderRow)}
        </>
      )}
      {done.length > 0 && (
        <>
          <div style={{ fontSize: 11, color: '#8c8c8c', padding: '8px 12px 4px', textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 500 }}>
            已完成
          </div>
          {done.map(renderRow)}
        </>
      )}
      {subtasks.length === 0 && !loading && (
        <div style={{ textAlign: 'center', padding: 16, color: '#bbb', fontSize: 13 }}>
          暂无子任务，点击上方按钮或使用 AI 拆解创建
        </div>
      )}

      <Modal
        title="添加子任务"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={saving}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ type: 'subtask', priority: 'medium' }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="子任务标题" />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="type" label="类型" style={{ width: 120 }}>
              <Select
                options={[
                  { label: '子任务', value: 'subtask' },
                  { label: '任务', value: 'task' },
                  { label: 'Bug', value: 'bug' },
                ]}
              />
            </Form.Item>
            <Form.Item name="priority" label="优先级" style={{ width: 120 }}>
              <Select
                options={[
                  { label: '低', value: 'low' },
                  { label: '中', value: 'medium' },
                  { label: '高', value: 'high' },
                  { label: '紧急', value: 'urgent' },
                ]}
              />
            </Form.Item>
            <Form.Item name="estimatedHours" label="预估工时" style={{ width: 100 }}>
              <InputNumber min={0} step={0.5} placeholder="小时" style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="assigneeId" label="负责人">
            <UserSelect placeholder="选择负责人" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/** 悬停时显示的操作按钮 */
function ActionButtons({
  subtask,
  onStart,
  onDone,
  onReopen,
  onDelete,
}: {
  subtask: Task;
  onStart: (t: Task) => void;
  onDone: (t: Task) => void;
  onReopen: (t: Task) => void;
  onDelete: (t: Task) => void;
}) {
  return (
    <span
      className="subtask-actions"
      style={{ display: 'none', gap: 2, flexShrink: 0 }}
      onClick={(e) => e.stopPropagation()}
    >
      {subtask.status === 'todo' && (
        <Tooltip title="开始">
          <Button size="small" type="text" icon={<CaretRightOutlined />} onClick={() => onStart(subtask)} />
        </Tooltip>
      )}
      {subtask.status !== 'done' && (
        <Tooltip title="完成">
          <Button size="small" type="text" icon={<CheckOutlined style={{ color: '#52c41a' }} />} onClick={() => onDone(subtask)} />
        </Tooltip>
      )}
      {subtask.status === 'done' && (
        <Tooltip title="重开">
          <Button size="small" type="text" icon={<UndoOutlined />} onClick={() => onReopen(subtask)} />
        </Tooltip>
      )}
      <Popconfirm title="确定删除此子任务？" onConfirm={() => onDelete(subtask)}>
        <Tooltip title="删除">
          <Button size="small" type="text" danger icon={<DeleteOutlined />} />
        </Tooltip>
      </Popconfirm>
    </span>
  );
}

/* 注入悬停样式 */
const style = document.createElement('style');
style.textContent = `
  .ant-list-item:hover .subtask-actions { display: inline-flex !important; }
`;
if (!document.head.querySelector('[data-subtask-style]')) {
  style.setAttribute('data-subtask-style', '1');
  document.head.appendChild(style);
}
