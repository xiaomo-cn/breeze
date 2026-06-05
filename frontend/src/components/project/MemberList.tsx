import { useEffect, useState } from 'react';
import { Table, Select, Button, Popconfirm, message, Avatar } from 'antd';
import { UserAddOutlined, DeleteOutlined } from '@ant-design/icons';
import {
  listProjectMembers,
  updateProjectMemberRole,
  removeProjectMember,
} from '../../api/projects';
import type { ProjectMember } from '../../types';
import { ROLES } from '../../constants';
import AddMemberModal from './AddMemberModal';

interface Props {
  projectId: number;
  ownerId: number;
}

export default function MemberList({ projectId, ownerId }: Props) {
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [loading, setLoading] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);

  const fetchMembers = async () => {
    setLoading(true);
    try {
      const data = await listProjectMembers(projectId);
      setMembers(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMembers();
  }, [projectId]);

  const handleRoleChange = async (userId: number, role: string) => {
    try {
      await updateProjectMemberRole(projectId, userId, role);
      message.success('角色已更新');
      setMembers((prev) =>
        prev.map((m) => (m.userId === userId ? { ...m, role } : m)),
      );
    } catch {
      message.error('更新角色失败');
    }
  };

  const handleRemove = async (userId: number) => {
    try {
      await removeProjectMember(projectId, userId);
      message.success('已移除成员');
      setMembers((prev) => prev.filter((m) => m.userId !== userId));
    } catch {
      message.error('移除成员失败');
    }
  };

  const columns = [
    {
      title: '用户',
      key: 'user',
      render: (_: unknown, record: ProjectMember) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Avatar size="small" style={{ backgroundColor: '#1677ff' }}>
            {String(record.userId)}
          </Avatar>
          <span>用户 #{record.userId}</span>
          {record.userId === ownerId && (
            <span style={{ color: '#999', fontSize: 12 }}>(创建者)</span>
          )}
        </div>
      ),
    },
    {
      title: '角色',
      key: 'role',
      render: (_: unknown, record: ProjectMember) => {
        if (record.userId === ownerId) {
          return <Select value="admin" disabled style={{ width: 120 }} />;
        }
        return (
          <Select
            value={record.role}
            onChange={(role) => handleRoleChange(record.userId, role)}
            style={{ width: 120 }}
            options={Object.values(ROLES).map((r) => ({
              value: r.value,
              label: r.label,
            }))}
          />
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      render: (_: unknown, record: ProjectMember) => {
        if (record.userId === ownerId) return null;
        return (
          <Popconfirm
            title="确定移除此成员？"
            onConfirm={() => handleRemove(record.userId)}
          >
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<UserAddOutlined />}
          onClick={() => setAddModalOpen(true)}
        >
          添加成员
        </Button>
      </div>
      <Table
        dataSource={members}
        columns={columns}
        rowKey="userId"
        loading={loading}
        pagination={false}
      />
      <AddMemberModal
        projectId={projectId}
        open={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        onAdded={fetchMembers}
      />
    </div>
  );
}
