import { Modal, Form, Select, Button } from 'antd';
import { useState } from 'react';
import { addProjectMember } from '../../api/projects';
import UserSelect from '../common/UserSelect';
import { ROLES } from '../../constants';

interface Props {
  projectId: number;
  open: boolean;
  onClose: () => void;
  onAdded: () => void;
}

export default function AddMemberModal({ projectId, open, onClose, onAdded }: Props) {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const handleAdd = async (values: { userId: number; role: string }) => {
    setLoading(true);
    try {
      await addProjectMember(projectId, values.userId, values.role);
      form.resetFields();
      onClose();
      onAdded();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="添加成员"
      open={open}
      onCancel={onClose}
      footer={null}
    >
      <Form form={form} onFinish={handleAdd} layout="vertical">
        <Form.Item
          name="userId"
          label="用户"
          rules={[{ required: true, message: '请选择用户' }]}
        >
          <UserSelect placeholder="搜索用户..." />
        </Form.Item>
        <Form.Item
          name="role"
          label="角色"
          initialValue="member"
          rules={[{ required: true, message: '请选择角色' }]}
        >
          <Select
            options={Object.values(ROLES).map((r) => ({
              value: r.value,
              label: r.label,
            }))}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          添加
        </Button>
      </Form>
    </Modal>
  );
}
