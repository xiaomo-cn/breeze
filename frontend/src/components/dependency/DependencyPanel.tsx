import { useEffect, useState, useRef } from 'react';
import { Button, Select, Space, List, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { listDependencies, createDependency, deleteDependency } from '../../api/dependencies';
import { searchTasks } from '../../api/tasks';
import type { Dependency, Task } from '../../types';

const TYPE_LABELS: Record<string, string> = {
  blocks: '阻塞', is_blocked_by: '被阻塞', relates_to: '关联',
};

interface Props {
  projectId: number;
  taskId: number;
}

export default function DependencyPanel({ projectId, taskId }: Props) {
  const [deps, setDeps] = useState<Dependency[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>();
  const [selectedType, setSelectedType] = useState<string>('blocks');
  const [taskOptions, setTaskOptions] = useState<{ label: string; value: number }[]>([]);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  function loadDeps() {
    listDependencies(taskId).then(setDeps).catch(() => {});
  }

  useEffect(() => { loadDeps(); }, [taskId]);

  const handleSearch = (keyword: string) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      if (!keyword || keyword.length < 1) return;
      searchTasks(projectId, { q: keyword, type: 'fulltext' }).then((res: any) => {
        const items = res.items || res || [];
        setTaskOptions(
          items.filter((t: Task) => t.id !== taskId)
            .map((t: Task) => ({ label: `${t.key} ${t.title}`, value: t.id })),
        );
      }).catch(() => {});
    }, 300);
  };

  async function handleAdd() {
    if (!selectedTaskId) return;
    setLoading(true);
    try {
      await createDependency(taskId, { dependsOnTaskId: selectedTaskId, type: selectedType });
      setSelectedTaskId(undefined);
      loadDeps();
    } catch (e: any) {
      message.error(e.response?.data?.message || '添加依赖失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(depId: number) {
    await deleteDependency(taskId, depId);
    loadDeps();
  }

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Select
          showSearch
          placeholder="搜索任务"
          style={{ width: 240 }}
          value={selectedTaskId}
          onChange={setSelectedTaskId}
          onSearch={handleSearch}
          options={taskOptions}
          filterOption={false}
        />
        <Select value={selectedType} onChange={setSelectedType} style={{ width: 100 }}
          options={[
            { label: '阻塞', value: 'blocks' },
            { label: '被阻塞', value: 'is_blocked_by' },
            { label: '关联', value: 'relates_to' },
          ]}
        />
        <Button icon={<PlusOutlined />} onClick={handleAdd} loading={loading}>
          添加
        </Button>
      </Space>

      <List
        dataSource={deps}
        renderItem={(dep) => (
          <List.Item
            actions={[
              <Popconfirm title="确认删除?" onConfirm={() => handleDelete(dep.id)}>
                <Button size="small" danger icon={<DeleteOutlined />} />
              </Popconfirm>,
            ]}
          >
            <Tag>{TYPE_LABELS[dep.type] || dep.type}</Tag>
            {dep.dependsOnTaskKey} {dep.dependsOnTaskTitle}
          </List.Item>
        )}
        locale={{ emptyText: '暂无依赖关系' }}
      />
    </div>
  );
}
