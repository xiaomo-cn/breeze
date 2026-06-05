import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Row, Col, Button, Modal, Form, Input, Typography, Empty, Spin, Pagination, Segmented, Statistic, Select, Badge, Space } from 'antd';
import { PlusOutlined, ProjectOutlined, ClockCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { listProjects, createProject } from '../api/projects';
import { getProjectRisks } from '../api/risks';
import ActivityTimeline from '../components/activity/ActivityTimeline';
import OverduePanel from '../components/dashboard/OverduePanel';
import RiskPanel from '../components/risk/RiskPanel';
import type { Project } from '../types';
import { useAuthStore } from '../stores/authStore';
import { useProjectStore } from '../stores/projectStore';

const { Title, Text } = Typography;

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<string>('active');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [projectRisks, setProjectRisks] = useState<Record<number, { high: number; medium: number; low: number }>>({});
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const isAdmin = useAuthStore((s) => s.role) === 'system_admin';
  const triggerRefresh = useProjectStore((s) => s.triggerRefresh);

  const fetchProjects = async () => {
    setLoading(true);
    try {
      const data = await listProjects({
        page,
        size: 12,
        status: statusFilter !== 'all' ? statusFilter : undefined,
      });
      setProjects(data.items);
      setTotal(data.total);

      // 并行拉取每个项目的风险数据
      const risksMap: Record<number, { high: number; medium: number; low: number }> = {};
      await Promise.all(data.items.map(async (p) => {
        try {
          const risks = await getProjectRisks(p.id);
          risksMap[p.id] = {
            high: risks.high.length,
            medium: risks.medium.length,
            low: risks.low.length,
          };
        } catch {
          // ignore
        }
      }));
      setProjectRisks(risksMap);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();
  }, [page, statusFilter]);

  const handleCreate = async (values: { name: string; key: string }) => {
    setCreating(true);
    try {
      const project = await createProject(values);
      triggerRefresh(); // 通知侧边栏等组件刷新项目列表
      setModalOpen(false);
      form.resetFields();
      navigate(`/projects/${project.id}`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <Title level={4} style={{ margin: 0, color: '#1e3a5f' }}>项目</Title>
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新建项目
          </Button>
        )}
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={6}>
          <Card style={{ background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(4px)', borderRadius: 12, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}>
            <Statistic title={<span style={{ color: '#5b7a9e' }}>项目总数</span>} value={total} valueStyle={{ color: '#1e293b' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card style={{ background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(4px)', borderRadius: 12, boxShadow: '0 2px 12px rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.06)' }}>
            <Statistic title={<span style={{ color: '#5b7a9e' }}>活跃项目</span>} value={total} suffix="" valueStyle={{ color: '#1e293b' }} />
          </Card>
        </Col>
      </Row>

      <div style={{ marginBottom: 16 }}>
        <Segmented
          value={statusFilter}
          onChange={(val) => {
            setStatusFilter(val as string);
            setPage(1);
          }}
          options={[
            { value: 'all', label: '全部' },
            { value: 'active', label: '活跃' },
            { value: 'archived', label: '已归档' },
            { value: 'completed', label: '已完成' },
          ]}
        />
      </div>

      {loading ? (
        <Spin style={{ display: 'block', margin: '40px auto' }} />
      ) : projects.length === 0 ? (
        <Empty description="暂无项目">
          {isAdmin ? (
            <Button type="primary" onClick={() => setModalOpen(true)}>
              创建第一个项目
            </Button>
          ) : (
            <span style={{ color: '#999' }}>请联系管理员创建项目</span>
          )}
        </Empty>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            {projects.map((p) => (
              <Col key={p.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  hoverable
                  onClick={() => navigate(`/projects/${p.id}`)}
                  style={{ background: 'rgba(255,255,255,0.8)', borderRadius: 10, boxShadow: '0 1px 6px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.05)' }}
                >
                  <div className="flex items-center gap-3">
                    <ProjectOutlined style={{ fontSize: 24, color: '#3b82f6' }} />
                    <div style={{ flex: 1 }}>
                      <Text strong>{p.name}</Text>
                      <br />
                      <Text type="secondary" style={{ fontSize: 12 }}>{p.key}</Text>
                    </div>
                    {projectRisks[p.id] && (projectRisks[p.id].high > 0 || projectRisks[p.id].medium > 0) && (
                      <Space size={4}>
                        {projectRisks[p.id].high > 0 && (
                          <Badge count={projectRisks[p.id].high} size="small" style={{ backgroundColor: '#ef4444' }} title="高风险" />
                        )}
                        {projectRisks[p.id].medium > 0 && (
                          <Badge count={projectRisks[p.id].medium} size="small" style={{ backgroundColor: '#f97316' }} title="中风险" />
                        )}
                      </Space>
                    )}
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
          <div style={{ marginTop: 24, textAlign: 'center' }}>
            <Pagination
              current={page}
              total={total}
              pageSize={12}
              onChange={setPage}
              showTotal={(t) => `共 ${t} 个项目`}
            />
          </div>
        </>
      )}

      <OverduePanel />

      <Card
        title={<><ClockCircleOutlined /> 最近活动</>}
        style={{ marginTop: 24, background: 'rgba(255,255,255,0.8)', borderRadius: 10, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}
      >
        <ActivityTimeline compact />
      </Card>

      <Card
        title={<><WarningOutlined /> 项目风险评估</>}
        style={{ marginTop: 24, background: 'rgba(255,255,255,0.8)', borderRadius: 10, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}
      >
        <div style={{ marginBottom: 16 }}>
          <Select
            placeholder="选择一个项目查看风险评估"
            value={selectedProjectId}
            onChange={setSelectedProjectId}
            options={projects.map((p) => ({ value: p.id, label: `${p.key} - ${p.name}` }))}
            style={{ width: 320 }}
            allowClear
            filterOption={(input, option) =>
              (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
            }
            showSearch
          />
        </div>
        {selectedProjectId ? (
          <RiskPanel projectId={selectedProjectId} />
        ) : (
          <Empty description="请选择一个项目查看风险评估" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      <Modal
        title="创建项目"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
      >
        <Form form={form} onFinish={handleCreate} layout="vertical">
          <Form.Item
            name="name"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="我的项目" />
          </Form.Item>
          <Form.Item
            name="key"
            label="项目标识"
            rules={[
              { required: true, message: '请输入项目标识' },
              { pattern: /^[A-Z]+$/, message: '标识必须为大写字母' },
            ]}
          >
            <Input placeholder="MP" maxLength={10} style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={creating} block>
            创建
          </Button>
        </Form>
      </Modal>
    </div>
  );
}
