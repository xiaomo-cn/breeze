import { Card, Table, Statistic, Row, Col, Progress } from 'antd';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { SprintReport } from '../../types';

export default function SprintReportView({ report }: { report: SprintReport }) {
  const ratePercent = Math.round(report.completionRate * 100);

  const memberColumns = [
    { title: '成员', dataIndex: 'userName' },
    { title: '完成数', dataIndex: 'completed' },
    { title: '负责数', dataIndex: 'created' },
  ];

  const burndownData = report.burndown.map(p => ({
    date: p.date,
    ideal: p.idealRemaining,
    actual: p.actualRemaining >= 0 ? p.actualRemaining : undefined,
  }));

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="完成率" value={ratePercent} suffix="%"
            valueStyle={{ color: ratePercent >= 80 ? '#52c41a' : '#faad14' }} /></Col>
          <Col span={6}><Statistic title="总任务" value={report.totalTasks} /></Col>
          <Col span={6}><Statistic title="已完成" value={report.completedTasks} /></Col>
          <Col span={6}><Statistic title="故事点"
            value={`${report.completedStoryPoints}/${report.totalStoryPoints}`} /></Col>
        </Row>
        <Progress percent={ratePercent} style={{ marginTop: 12 }} />
      </Card>

      <Card title="燃尽图" size="small" style={{ marginBottom: 16 }}>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={burndownData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" />
            <YAxis allowDecimals={false} />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="ideal" stroke="#faad14" name="理想线"
              strokeDasharray="5 5" />
            <Line type="monotone" dataKey="actual" stroke="#1890ff" name="实际线" />
          </LineChart>
        </ResponsiveContainer>
      </Card>

      <Card title="成员贡献" size="small">
        <Table dataSource={report.contributions} columns={memberColumns}
          rowKey="userName" pagination={false} size="small" />
      </Card>
    </div>
  );
}
