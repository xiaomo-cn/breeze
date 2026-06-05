import { useState } from 'react';
import { Button, Table, message, Modal, Typography } from 'antd';
import { RobotOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface ScheduleItem {
  taskId: number;
  suggestedAssigneeId: number | null;
  suggestedStartDate: string;
  suggestedEndDate: string;
  reason: string;
  /** 来自后端异常的占位对象 */
  error?: string;
  raw?: string;
}

interface Props {
  sprintId: number;
}

/**
 * AI 排期建议面板 — 调用后端 AI 接口为 Sprint 任务生成排期方案。
 */
export default function SchedulingPanel({ sprintId }: Props) {
  const [loading, setLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<ScheduleItem[]>([]);
  const [open, setOpen] = useState(false);

  const handleGenerate = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem('accessToken');
      const res = await fetch(`/api/v1/ai/suggestions/scheduling/${sprintId}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed');
      const data = await res.json();
      setSuggestions(data);
      setOpen(true);
    } catch {
      message.error('排期建议生成失败');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: '任务ID', dataIndex: 'taskId', width: 80 },
    { title: '建议指派人', dataIndex: 'suggestedAssigneeId', width: 100 },
    { title: '开始日期', dataIndex: 'suggestedStartDate', width: 110 },
    { title: '结束日期', dataIndex: 'suggestedEndDate', width: 110 },
    { title: '理由', dataIndex: 'reason' },
  ];

  return (
    <>
      <Button icon={<RobotOutlined />} onClick={handleGenerate} loading={loading}>
        AI 排期建议
      </Button>
      <Modal
        title="AI 排期建议"
        open={open}
        onCancel={() => setOpen(false)}
        width={800}
        footer={null}
      >
        {suggestions.length > 0 && !suggestions[0].error ? (
          <Table
            dataSource={suggestions}
            columns={columns}
            rowKey="taskId"
            size="small"
            pagination={false}
          />
        ) : (
          <Text type="secondary">未能生成排期建议</Text>
        )}
      </Modal>
    </>
  );
}
