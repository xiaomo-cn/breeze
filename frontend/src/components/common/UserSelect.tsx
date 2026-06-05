import { useState, useEffect, useMemo } from 'react';
import { Select, Spin, Tag } from 'antd';
import { listUsers } from '../../api/users';
import { listPositions } from '../../api/admin';
import type { User, Position } from '../../types';

interface SingleProps {
  mode?: 'single';
  value?: number;
  onChange?: (userId: number | undefined) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

interface MultipleProps {
  mode: 'multiple';
  value?: number[];
  onChange?: (userIds: number[]) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

type Props = SingleProps | MultipleProps;

export default function UserSelect(props: Props) {
  const { mode = 'single', value, onChange, placeholder = '选择负责人', style } = props;
  const [users, setUsers] = useState<User[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  // 加载所有活跃用户和职务
  useEffect(() => {
    setLoading(true);
    Promise.all([
      listUsers({ size: 200 }),
      listPositions().catch(() => [] as Position[]),
    ])
      .then(([userData, posData]) => {
        setUsers(userData.items);
        setPositions(posData);
      })
      .catch(() => setUsers([]))
      .finally(() => setLoading(false));
  }, []);

  /** 根据 positionId 查找职务 */
  const getPosition = (positionId?: number | null): Position | undefined => {
    if (positionId == null) return undefined;
    return positions.find((p) => p.id === positionId);
  };

  // 客户端搜索过滤
  const filteredUsers = useMemo(() => {
    if (!search) return users;
    const q = search.toLowerCase();
    return users.filter(
      (u) =>
        (u.displayName && u.displayName.toLowerCase().includes(q)) ||
        u.username.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q),
    );
  }, [users, search]);

  const options = filteredUsers.map((u) => {
    const pos = getPosition(u.positionId);
    return {
      value: u.id,
      label: (
        <span>
          {u.displayName || u.username}
          <span style={{ color: '#999', marginLeft: 4 }}>(@{u.username})</span>
          {pos && (
            <Tag color={pos.color} style={{ marginLeft: 6, fontSize: 11, lineHeight: '18px', padding: '0 4px' }}>
              {pos.name}
            </Tag>
          )}
        </span>
      ),
    };
  });

  if (mode === 'multiple') {
    return (
      <Select
        mode="multiple"
        showSearch
        allowClear
        value={value as number[] | undefined}
        onChange={onChange as ((ids: number[]) => void) | undefined}
        placeholder={placeholder}
        filterOption={false}
        onSearch={setSearch}
        onDropdownVisibleChange={(open) => { if (open) setSearch(''); }}
        loading={loading}
        notFoundContent={loading ? <Spin size="small" /> : '无匹配用户'}
        options={options}
        style={style}
      />
    );
  }

  return (
    <Select
      showSearch
      allowClear
      value={value as number | undefined}
      onChange={onChange as ((id: number | undefined) => void) | undefined}
      placeholder={placeholder}
      filterOption={false}
      onSearch={setSearch}
      onDropdownVisibleChange={(open) => { if (open) setSearch(''); }}
      loading={loading}
      notFoundContent={loading ? <Spin size="small" /> : '无匹配用户'}
      options={options}
      style={style}
    />
  );
}
