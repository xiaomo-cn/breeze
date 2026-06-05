import { Card, Form, Input, Select, Button, message, Spin, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { useAuthStore } from '../stores/authStore';
import client from '../api/client';
import { listPositions } from '../api/admin';
import type { User, Position } from '../types';

const timezoneOptions = [
  'Asia/Shanghai', 'Asia/Tokyo', 'Asia/Singapore', 'Asia/Kolkata',
  'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles',
  'America/Chicago', 'Pacific/Auckland', 'UTC',
].map((tz) => ({ label: tz, value: tz }));

const localeOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
];

export default function ProfilePage() {
  const { username } = useAuthStore();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [profile, setProfile] = useState<User | null>(null);
  const [positions, setPositions] = useState<Position[]>([]);

  useEffect(() => {
    Promise.all([
      client.get('/users/me'),
      listPositions().catch(() => [] as Position[]),
    ]).then(([userRes, pos]) => {
      setProfile(userRes.data);
      setPositions(pos);
      form.setFieldsValue({
        displayName: userRes.data.displayName,
        positionId: userRes.data.positionId ?? undefined,
        title: userRes.data.title,
        department: userRes.data.department,
        timezone: userRes.data.timezone,
        locale: userRes.data.locale,
      });
    }).catch(() => {
      message.error('加载个人信息失败');
    }).finally(() => setLoading(false));
  }, [form]);

  const handleSave = async (values: Record<string, unknown>) => {
    setSaving(true);
    try {
      await client.patch('/users/me', {
        displayName: values.displayName,
        positionId: values.positionId != null ? String(values.positionId) : null,
        title: values.title,
        department: values.department,
        timezone: values.timezone,
        locale: values.locale,
      });
      message.success('个人信息已更新');
    } catch {
      message.error('更新失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Spin style={{ display: 'block', textAlign: 'center', padding: 48 }} />;
  }

  return (
    <div style={{ maxWidth: 560, margin: '0 auto' }}>
      <Card title="个人设置" style={{ background: 'rgba(255,255,255,0.8)', borderRadius: 12, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item label="用户名">
            <Input value={username || profile?.username || ''} disabled />
          </Form.Item>
          <Form.Item label="邮箱">
            <Input value={profile?.email || ''} disabled />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称">
            <Input placeholder="输入显示名称" />
          </Form.Item>
          <Form.Item name="positionId" label="职务">
            <Select
              allowClear
              placeholder="选择职务"
              options={positions.map((p) => ({ value: p.id, label: p.name }))}
              optionRender={(option) => {
                const pos = positions.find((p) => p.id === option.value);
                return pos ? <Tag color={pos.color}>{pos.name}</Tag> : <span>{option.label}</span>;
              }}
            />
          </Form.Item>
          <Form.Item name="title" label="职位描述">
            <Input placeholder="如：高级前端工程师（可选）" />
          </Form.Item>
          <Form.Item name="department" label="部门">
            <Input placeholder="如：技术部" />
          </Form.Item>
          <Form.Item name="timezone" label="时区">
            <Select options={timezoneOptions} showSearch placeholder="选择时区" />
          </Form.Item>
          <Form.Item name="locale" label="语言">
            <Select options={localeOptions} placeholder="选择语言偏好" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={saving}>
              保存
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
