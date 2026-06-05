import { Form, Input, Select, DatePicker, InputNumber, Button, Space } from 'antd';
import dayjs from 'dayjs';
import UserSelect from '../common/UserSelect';
import { TASK_PRIORITIES, TASK_TYPES } from '../../constants';
import type { Task } from '../../types';

interface Props {
  task: Task;
  onSave: (values: Partial<Task>) => Promise<void>;
  onCancel: () => void;
  saving: boolean;
  statusOptions?: { value: string; label: string }[];
}

export default function TaskEditForm({ task, onSave, onCancel, saving, statusOptions }: Props) {
  const [form] = Form.useForm();

  const initialValues = {
    title: task.title,
    status: task.status,
    priority: task.priority,
    type: task.type,
    assigneeId: task.assigneeId,
    collaboratorIds: task.collaboratorIds || [],
    storyPoints: task.storyPoints,
    dueDate: task.dueDate ? dayjs(task.dueDate) : undefined,
    description: task.description,
  };

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onFinish={(values) => {
        // 从协作人中排除负责人
        const collaboratorIds = (values.collaboratorIds || []).filter((id: number) => id !== values.assigneeId);
        onSave({
          ...values,
          collaboratorIds,
          dueDate: values.dueDate?.format('YYYY-MM-DD'),
        });
      }}
    >
      <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
        <Input />
      </Form.Item>
      <Form.Item name="status" label="状态">
        <Select
          options={statusOptions?.length ? statusOptions : undefined}
        />
      </Form.Item>
      <Form.Item name="priority" label="优先级">
        <Select
          options={Object.values(TASK_PRIORITIES).map((p) => ({
            value: p.value,
            label: p.label,
          }))}
        />
      </Form.Item>
      <Form.Item name="type" label="类型">
        <Select
          options={Object.values(TASK_TYPES).map((t) => ({
            value: t.value,
            label: t.label,
          }))}
        />
      </Form.Item>
      <Form.Item name="assigneeId" label="负责人">
        <UserSelect placeholder="选择负责人" />
      </Form.Item>
      <Form.Item name="collaboratorIds" label="协作人">
        <UserSelect mode="multiple" placeholder="选择协作人（可选）" />
      </Form.Item>
      <Form.Item name="storyPoints" label="故事点">
        <InputNumber min={1} max={21} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="dueDate" label="截止日期">
        <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
      </Form.Item>
      <Form.Item name="description" label="描述">
        <Input.TextArea rows={4} placeholder="任务描述" />
      </Form.Item>
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={saving}>
            保存修改
          </Button>
          <Button onClick={onCancel}>取消</Button>
        </Space>
      </Form.Item>
    </Form>
  );
}
