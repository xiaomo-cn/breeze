import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Typography, Button, Modal, Form, Input, DatePicker, message, Card, Tag, Space, Empty } from 'antd';
import { PlusOutlined, PlayCircleOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { listSprints, createSprint, startSprint, closeSprint } from '../api/sprints';
import type { Sprint } from '../types';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const statusColors: Record<string, string> = { planning: 'blue', active: 'green', closed: 'default' };
const statusLabels: Record<string, string> = { planning: '规划中', active: '进行中', closed: '已关闭' };

export default function SprintPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      setSprints(await listSprints(projectId));
    } catch {
      message.error('加载 Sprint 列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [projectId]);

  const handleCreate = async () => {
    const values = await form.validateFields();
    await createSprint(projectId, {
      name: values.name,
      goal: values.goal,
      startDate: values.dates?.[0]?.format('YYYY-MM-DD'),
      endDate: values.dates?.[1]?.format('YYYY-MM-DD'),
    });
    message.success('Sprint 创建成功');
    setModalOpen(false);
    form.resetFields();
    load();
  };

  const handleStart = async (sprintId: number) => {
    await startSprint(projectId, sprintId);
    message.success('Sprint 已启动');
    load();
  };

  const handleClose = async (sprintId: number) => {
    Modal.confirm({
      title: '关闭 Sprint',
      content: '未完成的任务将移回 Backlog，确定关闭？',
      onOk: async () => {
        await closeSprint(projectId, sprintId);
        message.success('Sprint 已关闭');
        load();
      },
    });
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0, color: '#1e3a5f' }}>Sprint 管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          新建 Sprint
        </Button>
      </div>

      {sprints.length === 0 && !loading ? (
        <Empty description="暂无 Sprint" />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16 }}>
          <div>
            {sprints.map((s) => (
              <Card
                key={s.id}
                hoverable
                onClick={() => navigate(`/projects/${projectId}/sprints/${s.id}`)}
                style={{ marginBottom: 12, background: 'rgba(255,255,255,0.8)', borderRadius: 10, boxShadow: '0 2px 10px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Space>
                    <Text strong>{s.name}</Text>
                    <Tag color={statusColors[s.status]}>{statusLabels[s.status]}</Tag>
                  </Space>
                  <Space>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {s.completedTaskCount}/{s.taskCount} 任务
                    </Text>
                    {s.status === 'planning' && (
                      <Button size="small" icon={<PlayCircleOutlined />} onClick={(e) => { e.stopPropagation(); handleStart(s.id); }}>
                        启动
                      </Button>
                    )}
                    {s.status === 'active' && (
                      <Button size="small" icon={<CheckCircleOutlined />} onClick={(e) => { e.stopPropagation(); handleClose(s.id); }}>
                        关闭
                      </Button>
                    )}
                  </Space>
                </div>
                {s.goal && <Text type="secondary" style={{ fontSize: 12 }}>{s.goal}</Text>}
                {s.startDate && s.endDate && (
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {s.startDate} ~ {s.endDate}
                    </Text>
                  </div>
                )}
              </Card>
            ))}
          </div>
        </div>
      )}

      <Modal title="新建 Sprint" open={modalOpen} onOk={handleCreate} onCancel={() => setModalOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入 Sprint 名称' }]}>
            <Input placeholder="Sprint 1" />
          </Form.Item>
          <Form.Item name="goal" label="目标">
            <Input.TextArea rows={2} placeholder="本次 Sprint 的目标..." />
          </Form.Item>
          <Form.Item name="dates" label="起止日期">
            <RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
