import { Card, Table, Statistic, Row, Col } from 'antd';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, PieChart, Pie, Cell, ResponsiveContainer, Legend } from 'recharts';
import type { WeeklyReport } from '../../types';

const COLORS = ['#52c41a', '#1890ff', '#faad14', '#f5222d', '#722ed1'];

export default function WeeklyReportView({ report }: { report: WeeklyReport }) {
  const distData = Object.entries(report.taskDistribution).map(([name, value]) => ({
    name, value,
  }));

  const memberColumns = [
    { title: '成员', dataIndex: 'userName' },
    { title: '完成数', dataIndex: 'completed' },
    { title: '负责数', dataIndex: 'created' },
  ];

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}><Card><Statistic title="新增任务" value={report.newTasks} /></Card></Col>
        <Col span={8}><Card><Statistic title="完成任务" value={report.completedTasks}
          valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={8}><Card><Statistic title="剩余任务" value={report.remainingTasks}
          valueStyle={{ color: '#faad14' }} /></Card></Col>
      </Row>

      <Card title="每日趋势" size="small" style={{ marginBottom: 16 }}>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={report.dailyPoints}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" />
            <YAxis allowDecimals={false} />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="created" stroke="#1890ff" name="创建" />
            <Line type="monotone" dataKey="completed" stroke="#52c41a" name="完成" />
          </LineChart>
        </ResponsiveContainer>
      </Card>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <Card title="任务分布" size="small">
            <ResponsiveContainer width="100%" height={250}>
              <PieChart>
                <Pie data={distData} dataKey="value" nameKey="name" cx="50%" cy="50%"
                  outerRadius={80} label>
                  {distData.map((_, i) => (<Cell key={i} fill={COLORS[i % COLORS.length]} />))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="成员贡献" size="small">
            <Table dataSource={report.contributions} columns={memberColumns}
              rowKey="userName" pagination={false} size="small" />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
