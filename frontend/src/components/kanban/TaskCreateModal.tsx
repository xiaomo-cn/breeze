import { useState } from 'react';
import { Button, Modal, Form, Input, Select, DatePicker, InputNumber } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { createTask } from '../../api/tasks';
import { useKanbanStore } from '../../stores/kanbanStore';
import UserSelect from '../common/UserSelect';
import { TASK_PRIORITIES, TASK_TYPES } from '../../constants';
import type { Task } from '../../types';

interface Props {
  projectId: number;
}

export default function TaskCreateModal({ projectId }: Props) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const addTask = useKanbanStore((s) => s.addTask);

  const handleCreate = async (values: Partial<Task> & { dueDate?: { format: (f: string) => string } }) => {
    setLoading(true);
    try {
      // 从协作人中排除负责人
      const collaboratorIds = (values.collaboratorIds || []).filter((id: number) => id !== values.assigneeId);
      const task = await createTask(projectId, {
        ...values,
        collaboratorIds,
        dueDate: values.dueDate?.format('YYYY-MM-DD'),
      });
      addTask(task);
      setOpen(false);
      form.resetFields();
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
        创建任务
      </Button>
      <Modal
        title="创建任务"
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
      >
        <Form form={form} onFinish={handleCreate} layout="vertical">
          <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: '请输入任务标题' }]}
          >
            <Input placeholder="任务标题" />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue="task">
            <Select
              options={Object.values(TASK_TYPES).map((t) => ({
                value: t.value,
                label: t.label,
              }))}
            />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue="medium">
            <Select
              options={Object.values(TASK_PRIORITIES).map((p) => ({
                value: p.value,
                label: p.label,
              }))}
            />
          </Form.Item>
          <Form.Item name="assigneeId" label="负责人">
            <UserSelect placeholder="选择负责人" />
          </Form.Item>
          <Form.Item name="collaboratorIds" label="协作人">
            <UserSelect mode="multiple" placeholder="选择协作人（可选）" />
          </Form.Item>
          <Form.Item name="dueDate" label="截止日期">
            <DatePicker style={{ width: '100%' }} placeholder="选择截止日期" />
          </Form.Item>
          <Form.Item name="storyPoints" label="故事点">
            <InputNumber min={1} max={21} style={{ width: '100%' }} placeholder="故事点" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="任务描述" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>
            创建
          </Button>
        </Form>
      </Modal>
    </>
  );
}
