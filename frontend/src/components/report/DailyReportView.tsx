import { Descriptions, Table, Tag, Card } from 'antd';
import type { DailyReport } from '../../types';

const STATUS_COLORS: Record<string, string> = {
  done: 'green', in_progress: 'blue', blocked: 'red', todo: 'default',
};

export default function DailyReportView({ report }: { report: DailyReport }) {
  const columns = [
    { title: '编号', dataIndex: 'key', width: 100 },
    { title: '标题', dataIndex: 'title' },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: string) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', width: 100 },
    { title: '负责人', dataIndex: 'assigneeName', width: 100 },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="日期">{report.date}</Descriptions.Item>
          <Descriptions.Item label="创建任务">{report.createdCount}</Descriptions.Item>
          <Descriptions.Item label="完成任务">{report.completedCount}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="已完成任务" size="small" style={{ marginBottom: 16 }}>
        <Table dataSource={report.completedTasks} columns={columns} rowKey="id"
          pagination={false} size="small" locale={{ emptyText: '无' }} />
      </Card>
      <Card title="进行中任务" size="small" style={{ marginBottom: 16 }}>
        <Table dataSource={report.inProgressTasks} columns={columns} rowKey="id"
          pagination={false} size="small" locale={{ emptyText: '无' }} />
      </Card>
      <Card title="阻塞任务" size="small">
        <Table dataSource={report.blockedTasks} columns={columns} rowKey="id"
          pagination={false} size="small" locale={{ emptyText: '无' }} />
      </Card>
    </div>
  );
}
