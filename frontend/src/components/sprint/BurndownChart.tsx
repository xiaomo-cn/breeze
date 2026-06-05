import { useEffect, useState } from 'react';
import { Spin, Empty } from 'antd';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { getBurndown } from '../../api/sprints';
import type { BurndownPoint } from '../../types';

interface Props {
  projectId: number;
  sprintId: number;
}

export default function BurndownChart({ projectId, sprintId }: Props) {
  const [data, setData] = useState<BurndownPoint[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getBurndown(projectId, sprintId)
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [projectId, sprintId]);

  if (loading) return <Spin style={{ display: 'block', textAlign: 'center', padding: 24 }} />;
  if (data.length === 0) return <Empty description="暂无燃尽图数据" />;

  const chartData = data.map((p) => ({
    date: p.date,
    理想剩余: p.idealRemaining,
    实际剩余: p.actualRemaining >= 0 ? p.actualRemaining : null,
  }));

  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" fontSize={11} tickFormatter={(v: string) => v.slice(5)} />
        <YAxis fontSize={11} allowDecimals={false} />
        <Tooltip />
        <Legend />
        <Line type="linear" dataKey="理想剩余" stroke="#1677ff" strokeWidth={2} dot={false} />
        <Line type="linear" dataKey="实际剩余" stroke="#52c41a" strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  );
}
