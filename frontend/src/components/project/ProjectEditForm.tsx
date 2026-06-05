import { useEffect } from 'react';
import { Form, Input, Select, Button, message } from 'antd';
import { updateProject } from '../../api/projects';
import { useProjectStore } from '../../stores/projectStore';
import { PROJECT_STATUSES } from '../../constants';
import type { Project } from '../../types';

interface Props {
  project: Project;
  onUpdated: () => void;
}

export default function ProjectEditForm({ project, onUpdated }: Props) {
  const [form] = Form.useForm();
  const triggerRefresh = useProjectStore((s) => s.triggerRefresh);

  useEffect(() => {
    form.setFieldsValue({
      name: project.name,
      key: project.key,
      description: project.description || '',
      status: project.status,
    });
  }, [project.id, form]);

  const handleSave = async (values: { name: string; description?: string; status: string }) => {
    try {
      await updateProject(project.id, values);
      triggerRefresh(); // 通知侧边栏刷新项目名称等信息
      message.success('项目已更新');
      onUpdated();
    } catch {
      message.error('更新项目失败');
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      style={{ maxWidth: 480 }}
      initialValues={{
        name: project.name,
        key: project.key,
        description: project.description || '',
        status: project.status,
      }}
      onFinish={handleSave}
    >
      <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
        <Input />
      </Form.Item>
      <Form.Item name="key" label="项目标识">
        <Input disabled />
      </Form.Item>
      <Form.Item name="description" label="描述">
        <Input.TextArea rows={3} placeholder="项目描述" />
      </Form.Item>
      <Form.Item name="status" label="状态">
        <Select
          options={Object.values(PROJECT_STATUSES).map((s) => ({
            value: s.value,
            label: s.label,
          }))}
        />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit">
          保存修改
        </Button>
      </Form.Item>
    </Form>
  );
}
