import { useEffect, useState } from 'react';
import { Card, Tag, List, Typography, Space, Spin, Empty, Button } from 'antd';
import { WarningOutlined, ReloadOutlined } from '@ant-design/icons';
import { getProjectRisks, assessProject, type ProjectRisks, type RiskItem } from '../../api/risks';

const { Text } = Typography;

const riskColors: Record<string, string> = { high: 'red', medium: 'orange', low: 'green' };
const riskLabels: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险' };

function RiskSection({ title, items, color }: { title: string; items: RiskItem[]; color: string }) {
  if (items.length === 0) return null;
  return (
    <div style={{ marginBottom: 16 }}>
      <Text strong style={{ color }}>{title} ({items.length})</Text>
      <List
        size="small"
        dataSource={items}
        renderItem={(item) => (
          <List.Item style={{ padding: '4px 0', fontSize: 12 }}>
            <Space>
              <Tag color={riskColors[item.riskLevel]}>{riskLabels[item.riskLevel]}</Tag>
              <Text>{item.key}</Text>
              <Text>{item.title}</Text>
            </Space>
            <Text type="secondary" style={{ fontSize: 11 }}>{item.riskReason}</Text>
          </List.Item>
        )}
      />
    </div>
  );
}

interface Props { projectId: number; }

export default function RiskPanel({ projectId }: Props) {
  const [risks, setRisks] = useState<ProjectRisks | null>(null);
  const [loading, setLoading] = useState(false);
  const [assessing, setAssessing] = useState(false);

  const fetchRisks = () => {
    setLoading(true);
    getProjectRisks(projectId).then(setRisks).catch(() => {}).finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchRisks();
  }, [projectId]);

  const handleAssess = async () => {
    setAssessing(true);
    try {
      await assessProject(projectId);
      await fetchRisks();
    } catch {
      // ignore
    } finally {
      setAssessing(false);
    }
  };

  if (loading) return <Spin />;
  if (!risks || (risks.high.length === 0 && risks.medium.length === 0 && risks.low.length === 0)) {
    return <Empty description="无风险评估数据" />;
  }

  const total = risks.high.length + risks.medium.length + risks.low.length;

  return (
    <Card
      title={<Space><WarningOutlined />风险评估</Space>}
      extra={
        <Space>
          <Tag>{total} 个风险</Tag>
          <Button
            size="small"
            icon={<ReloadOutlined spin={assessing} />}
            loading={assessing}
            onClick={handleAssess}
          >
            重新评估
          </Button>
        </Space>
      }
      size="small"
    >
      <RiskSection title="高风险" items={risks.high} color="red" />
      <RiskSection title="中风险" items={risks.medium} color="orange" />
      <RiskSection title="低风险" items={risks.low} color="green" />
    </Card>
  );
}
