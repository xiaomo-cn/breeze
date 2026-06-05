import { useEffect, useState } from 'react';
import { Timeline, Typography, Spin, Empty } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { listActivity } from '../../api/activity';
import type { ActivityLogEntry } from '../../types';
import client from '../../api/client';

const { Text } = Typography;

const actionLabels: Record<string, string> = {
  created: '创建了',
  updated: '更新了',
  deleted: '删除了',
  status_changed: '变更了状态',
  commented: '评论了',
  started: '启动了',
  closed: '关闭了',
  uploaded: '上传了文件到',
};

const entityLabels: Record<string, string> = {
  task: '任务',
  project: '项目',
  sprint: 'Sprint',
  comment: '评论',
};

function getDescription(entry: ActivityLogEntry): string {
  const action = actionLabels[entry.actionType] || entry.actionType;
  const entity = entityLabels[entry.entityType] || entry.entityType;
  const user = entry.displayName || entry.username || '某人';

  let detail = '';
  if (entry.details) {
    try {
      const d = JSON.parse(entry.details);
      if (d.title) detail = ` "${d.title}"`;
      else if (d.status) detail = ` 为 ${d.status}`;
      else if (d.name) detail = ` "${d.name}"`;
    } catch { /* ignore */ }
  }

  return `${user} ${action}${entity}${detail}`;
}

interface Props {
  projectId?: number;
  compact?: boolean;
}

export default function ActivityTimeline({ projectId, compact }: Props) {
  const [entries, setEntries] = useState<ActivityLogEntry[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    const fetcher = projectId
      ? () => listActivity(projectId, 1, compact ? 5 : 20)
      : async () => {
          const { data } = await client.get('/activity', {
            params: { page: 1, size: compact ? 5 : 20 },
          });
          return data;
        };
    fetcher()
      .then((data: any) => setEntries(data.items || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [projectId, compact]);

  if (loading) return <Spin style={{ display: 'block', textAlign: 'center', padding: 16 }} />;
  if (entries.length === 0) return <Empty description="暂无活动" image={Empty.PRESENTED_IMAGE_SIMPLE} />;

  return (
    <Timeline
      items={entries.map((e) => ({
        dot: <UserOutlined style={{ fontSize: 12 }} />,
        children: (
          <div>
            <Text style={{ fontSize: 13 }}>{getDescription(e)}</Text>
            <br />
            <Text type="secondary" style={{ fontSize: 11 }}>
              {new Date(e.createdAt).toLocaleString('zh-CN')}
            </Text>
          </div>
        ),
      }))}
    />
  );
}
