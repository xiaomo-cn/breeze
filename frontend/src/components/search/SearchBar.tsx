import { Input, Segmented, List, Typography, Spin } from 'antd';
import { useState } from 'react';
import { useSearch } from '../../hooks/useSearch';
import type { Task } from '../../types';

const { Text } = Typography;

interface Props {
  projectId: number;
  onResultClick: (taskId: number) => void;
}

export default function SearchBar({ projectId, onResultClick }: Props) {
  const [query, setQuery] = useState('');
  const [type, setType] = useState<'fulltext' | 'semantic'>('fulltext');
  const { results, loading, search, clear } = useSearch(projectId);

  const handleSearch = (value: string) => {
    if (!value.trim()) {
      clear();
      return;
    }
    setQuery(value);
    search(value, type);
  };

  const handleTypeChange = (val: string) => {
    const newType = val as 'fulltext' | 'semantic';
    setType(newType);
    if (query) search(query, newType);
  };

  const handleClick = (task: Task) => {
    onResultClick(task.id);
    clear();
    setQuery('');
  };

  return (
    <div style={{ position: 'relative', width: 360 }}>
      <Input.Search
        placeholder="搜索任务..."
        value={query}
        onChange={(e) => {
          const val = e.target.value;
          setQuery(val);
          if (val.trim()) {
            search(val, type);
          } else {
            clear();
          }
        }}
        onSearch={handleSearch}
        allowClear
        onClear={clear}
      />
      <Segmented
        value={type}
        onChange={handleTypeChange}
        options={[
          { value: 'fulltext', label: '全文搜索' },
          { value: 'semantic', label: '语义搜索' },
        ]}
        size="small"
        style={{ marginTop: 8, marginBottom: 8 }}
      />
      {(loading || results.length > 0) && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            background: '#fff',
            borderRadius: 8,
            boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
            zIndex: 1000,
            maxHeight: 320,
            overflow: 'auto',
          }}
        >
          {loading ? (
            <Spin style={{ display: 'block', padding: 16 }} />
          ) : (
            <List
              dataSource={results}
              renderItem={(task) => (
                <List.Item
                  style={{ padding: '8px 16px', cursor: 'pointer' }}
                  onClick={() => handleClick(task)}
                >
                  <div>
                    <Text style={{ fontSize: 12, color: '#999' }}>{task.key}</Text>
                    <br />
                    <Text>{task.title}</Text>
                  </div>
                </List.Item>
              )}
            />
          )}
        </div>
      )}
    </div>
  );
}
