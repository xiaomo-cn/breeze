import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Button, Modal, Drawer, Form, Input, Select, Switch, Space, message, Typography, Card, Tag, Popconfirm, Divider, ColorPicker } from 'antd';
import { PlusOutlined, ArrowLeftOutlined, EditOutlined, SettingOutlined } from '@ant-design/icons';
import { listUsers, createUser, updateUserRole, toggleUserStatus, updateUserProfile, resetUserPassword, listPositions, createPosition, updatePosition, deletePosition } from '../api/admin';
import { useAuthStore } from '../stores/authStore';
import { POSITION_COLOR_PRESETS } from '../constants';
import type { User, Position } from '../types';

const { Title } = Typography;

export default function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);

  // 创建用户
  const [createOpen, setCreateOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [createForm] = Form.useForm();

  // 编辑用户（Drawer）
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailSaving, setDetailSaving] = useState(false);
  const [detailResetting, setDetailResetting] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [detailForm] = Form.useForm();

  // 职务管理
  const [positionOpen, setPositionOpen] = useState(false);
  const [positionLoading, setPositionLoading] = useState(false);
  const [newPositionName, setNewPositionName] = useState('');
  const [newPositionColor, setNewPositionColor] = useState('blue');
  const [editingPositionId, setEditingPositionId] = useState<number | null>(null);
  const [editingPositionName, setEditingPositionName] = useState('');
  const [editingPositionColor, setEditingPositionColor] = useState('');

  const navigate = useNavigate();
  const currentRole = useAuthStore((s) => s.role);

  useEffect(() => {
    if (currentRole !== 'system_admin') {
      navigate('/');
    }
  }, [currentRole, navigate]);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await listUsers({ page, size: 20, search: search || undefined });
      setUsers(data.items);
      setTotal(data.total);
    } catch {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchPositions = async () => {
    try {
      const data = await listPositions();
      setPositions(data);
    } catch {
      // 静默失败
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [page, search]);

  useEffect(() => {
    fetchPositions();
  }, []);

  // ==================== 创建用户 ====================

  useEffect(() => {
    if (createOpen) createForm.resetFields();
  }, [createOpen, createForm]);

  const handleCreate = async (values: { username: string; email: string; password: string; displayName?: string; positionId?: number }) => {
    setCreateLoading(true);
    try {
      await createUser(values);
      message.success('用户创建成功');
      setCreateOpen(false);
      fetchUsers();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally {
      setCreateLoading(false);
    }
  };

  // ==================== 编辑用户详情 Drawer ====================

  const openDetail = (user: User) => {
    setEditingUser(user);
    detailForm.setFieldsValue({
      displayName: user.displayName || '',
      positionId: user.positionId ?? undefined,
      title: user.title || '',
      role: user.role || 'user',
      isActive: user.isActive,
    });
    setDetailOpen(true);
  };

  /** 保存基本资料 */
  const handleSaveProfile = async () => {
    if (!editingUser) return;
    const values = detailForm.getFieldsValue(['displayName', 'positionId', 'title']);
    setDetailSaving(true);
    try {
      await updateUserProfile(editingUser.id, values.displayName, values.title, values.positionId ?? null);
      message.success('资料已保存');
      fetchUsers();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败';
      message.error(msg);
    } finally {
      setDetailSaving(false);
    }
  };

  /** 切换角色 */
  const handleRoleChange = async () => {
    if (!editingUser) return;
    const role = detailForm.getFieldValue('role');
    setDetailSaving(true);
    try {
      await updateUserRole(editingUser.id, role);
      message.success('角色已更新');
      fetchUsers();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '更新失败';
      message.error(msg);
    } finally {
      setDetailSaving(false);
    }
  };

  /** 切换启停状态 */
  const handleStatusToggle = async (isActive: boolean) => {
    if (!editingUser) return;
    try {
      await toggleUserStatus(editingUser.id, isActive);
      message.success(isActive ? '账号已启用' : '账号已禁用');
      fetchUsers();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '操作失败';
      message.error(msg);
      detailForm.setFieldValue('isActive', !isActive);
    }
  };

  /** 重置密码 */
  const handleResetPassword = async () => {
    if (!editingUser) return;
    const newPassword = detailForm.getFieldValue('newPassword');
    if (!newPassword || newPassword.length < 6) {
      message.warning('新密码至少6个字符');
      return;
    }
    setDetailResetting(true);
    try {
      await resetUserPassword(editingUser.id, newPassword);
      message.success('密码已重置，用户下次登录需修改密码');
      detailForm.setFieldValue('newPassword', '');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '重置失败';
      message.error(msg);
    } finally {
      setDetailResetting(false);
    }
  };

  // ==================== 职务管理 ====================

  const handleAddPosition = async () => {
    if (!newPositionName.trim()) return;
    setPositionLoading(true);
    try {
      await createPosition({ name: newPositionName.trim(), color: newPositionColor });
      message.success('职务已添加');
      setNewPositionName('');
      setNewPositionColor('blue');
      fetchPositions();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '添加失败';
      message.error(msg);
    } finally {
      setPositionLoading(false);
    }
  };

  const handleUpdatePosition = async (id: number, name: string, color: string) => {
    try {
      await updatePosition(id, { name, color });
      message.success('职务已更新');
      setEditingPositionId(null);
      fetchPositions();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '更新失败';
      message.error(msg);
    }
  };

  const handleDeletePosition = async (id: number) => {
    try {
      await deletePosition(id);
      message.success('职务已删除');
      fetchPositions();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除失败';
      message.error(msg);
    }
  };

  /** 根据 positionId 查找职务信息 */
  const getPositionInfo = (positionId?: number | null): Position | undefined => {
    if (positionId == null) return undefined;
    return positions.find((p) => p.id === positionId);
  };

  // ==================== 表格列 ====================

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
    {
      title: '显示名', dataIndex: 'displayName', key: 'displayName',
      render: (name: string) => <span>{name || '-'}</span>,
    },
    {
      title: '职务', dataIndex: 'positionId', key: 'positionId', width: 130,
      render: (positionId: number | null, record: User) => {
        const pos = getPositionInfo(positionId);
        if (pos) return <Tag color={pos.color}>{pos.name}</Tag>;
        if (record.title) return <Tag>{record.title}</Tag>;
        return <span style={{ color: '#999' }}>-</span>;
      },
    },
    {
      title: '系统角色', dataIndex: 'role', key: 'role', width: 110,
      render: (role: string) => (
        <Tag color={role === 'system_admin' ? 'blue' : 'default'}>
          {role === 'system_admin' ? '管理员' : '普通用户'}
        </Tag>
      ),
    },
    {
      title: '状态', dataIndex: 'isActive', key: 'isActive', width: 80,
      render: (isActive: boolean) => (
        <Tag color={isActive ? 'green' : 'red'}>{isActive ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '操作', key: 'actions', width: 80,
      render: (_: unknown, record: User) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openDetail(record)}>
          编辑
        </Button>
      ),
    },
  ];

  if (currentRole !== 'system_admin') return null;

  return (
    <div style={{ padding: 24 }}>
      <Card
        style={{
          background: 'rgba(255,255,255,0.65)',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          borderRadius: 12,
          border: '1px solid rgba(59,130,246,0.08)',
        }}
      >
        <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')}>返回</Button>
            <Title level={4} style={{ margin: 0 }}>用户管理</Title>
          </Space>
          <Space>
            <Button icon={<SettingOutlined />} onClick={() => setPositionOpen(true)}>职务管理</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建用户</Button>
          </Space>
        </Space>

        <Input.Search
          placeholder="搜索用户名、邮箱、显示名"
          allowClear
          onSearch={(val) => { setSearch(val); setPage(1); }}
          style={{ marginBottom: 16, maxWidth: 400 }}
        />

        <Table
          rowKey="id"
          dataSource={users}
          columns={columns}
          loading={loading}
          scroll={{ x: 800 }}
          pagination={{
            current: page,
            total,
            pageSize: 20,
            onChange: setPage,
            showTotal: (t) => `共 ${t} 个用户`,
          }}
        />
      </Card>

      {/* ================== 创建用户弹窗 ================== */}
      <Modal
        title="创建用户"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate} autoComplete="off" initialValues={{ password: '123456' }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }, { min: 3, message: '至少3个字符' }]}>
            <Input placeholder="登录用户名" autoComplete="off" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
            <Input placeholder="user@example.com" autoComplete="off" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名">
            <Input placeholder="可选，默认使用用户名" autoComplete="off" />
          </Form.Item>
          <Form.Item name="positionId" label="职务">
            <Select allowClear placeholder="选择职务（可选）" options={positions.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }, { min: 6, message: '至少6个字符' }]}>
            <Input.Password placeholder="用户首次登录后可修改" autoComplete="new-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={createLoading} block>创建</Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* ================== 编辑用户详情 Drawer ================== */}
      <Drawer
        title={editingUser ? `编辑用户: ${editingUser.displayName || editingUser.username}` : '编辑用户'}
        open={detailOpen}
        onClose={() => { setDetailOpen(false); setEditingUser(null); }}
        width={420}
        destroyOnClose
        extra={
          <Button type="primary" loading={detailSaving} onClick={handleSaveProfile}>保存</Button>
        }
      >
        <Form form={detailForm} layout="vertical">
          {/* 基本资料 */}
          <Title level={5} style={{ marginBottom: 16 }}>基本资料</Title>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true, message: '请输入显示名' }]}>
            <Input placeholder="用户显示名称" />
          </Form.Item>
          <Form.Item name="positionId" label="职务">
            <Select allowClear placeholder="选择职务" options={positions.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          <Form.Item name="title" label="职位描述">
            <Input placeholder="如：高级前端工程师（可选）" />
          </Form.Item>

          <Divider />

          {/* 权限与状态 */}
          <Title level={5} style={{ marginBottom: 16 }}>权限与状态</Title>
          <Form.Item name="role" label="系统角色">
            <Select
              style={{ width: '100%' }}
              onChange={() => {
                // 切换后立即保存角色
                setTimeout(() => handleRoleChange(), 0);
              }}
              options={[
                { label: '系统管理员', value: 'system_admin' },
                { label: '普通用户', value: 'user' },
              ]}
            />
          </Form.Item>
          <Form.Item name="isActive" label="账号状态" valuePropName="checked">
            <Switch
              checkedChildren="启用"
              unCheckedChildren="禁用"
              onChange={(val) => handleStatusToggle(val)}
            />
          </Form.Item>

          <Divider />

          {/* 密码重置 */}
          <Title level={5} style={{ marginBottom: 16 }}>密码</Title>
          <Form.Item name="newPassword" label="重置密码">
            <Space style={{ width: '100%' }}>
              <Input.Password placeholder="输入新密码（至少6位）" style={{ width: 220 }} />
              <Button loading={detailResetting} onClick={handleResetPassword}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      {/* ================== 职务管理弹窗 ================== */}
      <Modal
        title="职务管理"
        open={positionOpen}
        onCancel={() => { setPositionOpen(false); setEditingPositionId(null); }}
        footer={null}
        width={520}
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>现有职务</div>
          <div style={{ maxHeight: 300, overflowY: 'auto' }}>
            {positions.map((pos) => (
              <div
                key={pos.id}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}
              >
                {editingPositionId === pos.id ? (
                  <div>
                    <Space style={{ marginBottom: 8 }}>
                      <Input size="small" value={editingPositionName} onChange={(e) => setEditingPositionName(e.target.value)} style={{ width: 120 }} />
                      <ColorPicker value={editingPositionColor} onChange={(_, hex) => setEditingPositionColor(hex)} format="hex" presets={POSITION_COLOR_PRESETS} />
                      <Button size="small" type="link" onClick={() => handleUpdatePosition(pos.id, editingPositionName, editingPositionColor)}>保存</Button>
                      <Button size="small" type="link" onClick={() => setEditingPositionId(null)}>取消</Button>
                    </Space>
                  </div>
                ) : (
                  <>
                    <Tag color={pos.color}>{pos.name}</Tag>
                    <Space>
                      <Button type="link" size="small" onClick={() => { setEditingPositionId(pos.id); setEditingPositionName(pos.name); setEditingPositionColor(pos.color); }}>编辑</Button>
                      <Popconfirm title="确定删除该职务？" description="已分配此职务的用户将被置空" onConfirm={() => handleDeletePosition(pos.id)} okText="确定" cancelText="取消">
                        <Button type="link" size="small" danger>删除</Button>
                      </Popconfirm>
                    </Space>
                  </>
                )}
              </div>
            ))}
          </div>
        </div>

        <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>新增职务</div>
          <Space>
            <Input placeholder="职务名称" value={newPositionName} onChange={(e) => setNewPositionName(e.target.value)} style={{ width: 160 }} onPressEnter={handleAddPosition} />
            <ColorPicker value={newPositionColor} onChange={(_, hex) => setNewPositionColor(hex)} format="hex" presets={POSITION_COLOR_PRESETS} />
            <Button type="primary" loading={positionLoading} onClick={handleAddPosition} disabled={!newPositionName.trim()}>添加</Button>
          </Space>
        </div>
      </Modal>
    </div>
  );
}
