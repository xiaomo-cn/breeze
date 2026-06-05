import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { changePassword } from '../api/users';
import { useAuthStore } from '../stores/authStore';

const { Title } = Typography;

export default function ChangePasswordPage() {
  const [loading, setLoading] = useState(false);
  const setAuth = useAuthStore((s) => s.setAuth);
  const navigate = useNavigate();

  const onFinish = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的新密码不一致');
      return;
    }
    setLoading(true);
    try {
      await changePassword(values.oldPassword, values.newPassword);
      // 更新 authStore，清除 mustChangePassword 标记
      const userId = useAuthStore.getState().userId!;
      const username = useAuthStore.getState().username!;
      const role = useAuthStore.getState().role!;
      const accessToken = localStorage.getItem('accessToken')!;
      const refreshToken = localStorage.getItem('refreshToken')!;
      setAuth({
        userId,
        username,
        role,
        mustChangePassword: false,
        accessToken,
        refreshToken,
        expiresIn: 3600,
      });
      message.success('密码修改成功');
      navigate('/');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '密码修改失败';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-transparent">
      <Card
        style={{
          width: 400,
          maxWidth: 'calc(100vw - 32px)',
          background: 'rgba(255,255,255,0.8)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          borderRadius: 16,
          boxShadow: '0 8px 32px rgba(59,130,246,0.12)',
          border: '1px solid rgba(59,130,246,0.1)',
        }}
      >
        <Title level={4} style={{ textAlign: 'center', marginBottom: 24 }}>
          首次登录，请修改密码
        </Title>
        <Form onFinish={onFinish} size="large">
          <Form.Item name="oldPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="当前密码" />
          </Form.Item>
          <Form.Item name="newPassword" rules={[
            { required: true, message: '请输入新密码' },
            { min: 6, message: '密码至少6个字符' },
          ]}>
            <Input.Password prefix={<LockOutlined />} placeholder="新密码（至少6个字符）" />
          </Form.Item>
          <Form.Item name="confirmPassword" rules={[
            { required: true, message: '请确认新密码' },
          ]}>
            <Input.Password prefix={<LockOutlined />} placeholder="确认新密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              修改密码
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
