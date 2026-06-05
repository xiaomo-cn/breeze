import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Segmented, DatePicker, Select, Button, Space, Spin, Modal, message, Typography, List, Popconfirm } from 'antd';
import { DownloadOutlined, RobotOutlined, CopyOutlined, FileTextOutlined, HistoryOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import DailyReportView from '../components/report/DailyReportView';
import WeeklyReportView from '../components/report/WeeklyReportView';
import SprintReportView from '../components/report/SprintReportView';
import { getDailyReport, getWeeklyReport, getSprintReport, getExportUrl, generateAiReport, listAiReports, type AiReportItem } from '../api/reports';
import { listSprints } from '../api/sprints';
import type { DailyReport, WeeklyReport, SprintReport, Sprint } from '../types';
import dayjs, { type Dayjs } from 'dayjs';

const { Text } = Typography;

type ReportType = 'daily' | 'weekly' | 'sprint';

/** AI 报告类型标签映射 */
const AI_TYPE_LABELS: Record<string, string> = {
  weekly: 'AI 周报',
  sprint_review: 'AI 回顾',
  project_summary: 'AI 总结',
};

export default function ReportPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);

  const [reportType, setReportType] = useState<ReportType>('daily');
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [weekRange, setWeekRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('week'), dayjs(),
  ]);
  const [sprintId, setSprintId] = useState<number | undefined>();
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [dailyReport, setDailyReport] = useState<DailyReport | null>(null);
  const [weeklyReport, setWeeklyReport] = useState<WeeklyReport | null>(null);
  const [sprintReport, setSprintReport] = useState<SprintReport | null>(null);
  const [loading, setLoading] = useState(false);

  const [aiLoading, setAiLoading] = useState(false);
  const [aiMarkdown, setAiMarkdown] = useState('');
  const [aiTitle, setAiTitle] = useState('');
  const [aiModalOpen, setAiModalOpen] = useState(false);

  // 历史报告相关状态
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [historyList, setHistoryList] = useState<AiReportItem[]>([]);
  const [currentAiType, setCurrentAiType] = useState('');

  useEffect(() => {
    listSprints(projectId).then(setSprints).catch(() => {});
  }, [projectId]);

  useEffect(() => {
    setLoading(true);
    if (reportType === 'daily') {
      getDailyReport(projectId, date.format('YYYY-MM-DD'))
        .then(setDailyReport).finally(() => setLoading(false));
    } else if (reportType === 'weekly') {
      getWeeklyReport(
        projectId,
        weekRange[0].format('YYYY-MM-DD'),
        weekRange[1].format('YYYY-MM-DD'),
      ).then(setWeeklyReport).finally(() => setLoading(false));
    } else if (reportType === 'sprint' && sprintId) {
      getSprintReport(projectId, sprintId)
        .then(setSprintReport).finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [projectId, reportType, date, weekRange, sprintId]);

  function handleExport(format: 'pdf' | 'csv') {
    const params: Record<string, string> = {};
    if (reportType === 'daily') params.date = date.format('YYYY-MM-DD');
    else if (reportType === 'weekly') {
      params.start = weekRange[0].format('YYYY-MM-DD');
      params.end = weekRange[1].format('YYYY-MM-DD');
    } else if (reportType === 'sprint' && sprintId) {
      params.sprintId = String(sprintId);
    }
    const url = getExportUrl(projectId, reportType, format, params);
    window.open(url, '_blank');
  }

  async function handleCopyMarkdown() {
    try {
      await navigator.clipboard.writeText(aiMarkdown);
      message.success('已复制到剪贴板');
    } catch {
      message.error('复制失败');
    }
  }

  function handleDownloadMarkdown() {
    const blob = new Blob([aiMarkdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `AI报告-${dayjs().format('YYYY-MM-DD-HHmm')}.md`;
    a.click();
    URL.revokeObjectURL(url);
    message.success('已下载 Markdown 文件');
  }

  /**
   * 点击"AI 周报/回顾/总结"按钮。
   * 先查历史，有历史则弹出选择框；无历史则直接生成。
   */
  const handleAiReportClick = async (type: string) => {
    setCurrentAiType(type);
    setAiLoading(true);
    try {
      const history = await listAiReports(projectId, type);
      if (history.length === 0) {
        await doGenerate(type);
      } else {
        setHistoryList(history);
        setHistoryModalOpen(true);
        setAiLoading(false);
      }
    } catch {
      // 查历史失败时也允许直接生成
      message.warning('无法查询历史记录，正在直接生成报告...');
      await doGenerate(type);
    }
  };

  /** 执行 AI 报告生成 + 保存 */
  const doGenerate = async (type: string) => {
    setAiLoading(true);
    setHistoryModalOpen(false);
    try {
      const report = await generateAiReport(projectId, type);
      setAiTitle(report.title);
      setAiMarkdown(report.content);
      setAiModalOpen(true);
      message.success('报告生成完成');
    } catch (err: any) {
      // 超时或网络错误时，告知用户可能已在后台完成
      if (err?.code === 'ECONNABORTED' || err?.message?.includes('timeout')) {
        message.warning('报告生成时间较长，可能已在后台完成，请稍后在历史记录中查看', 6);
        // 刷新历史列表
        try {
          const history = await listAiReports(projectId, type);
          if (history.length > 0) {
            const latest = history[0];
            setAiTitle(latest.title);
            setAiMarkdown(latest.content);
            setAiModalOpen(true);
          }
        } catch { /* ignore */ }
      } else {
        message.error('AI 报告生成失败，请稍后重试');
      }
    } finally {
      setAiLoading(false);
    }
  };

  /** 查看历史报告 */
  const viewHistoryReport = (report: AiReportItem) => {
    setAiTitle(report.title);
    setAiMarkdown(report.content);
    setAiModalOpen(true);
    setHistoryModalOpen(false);
  };

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="报表中心"
        style={{ background: 'rgba(255,255,255,0.8)', borderRadius: 12, boxShadow: '0 2px 12px rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.06)' }}
        extra={
          <Space>
            <Button icon={<RobotOutlined />} loading={aiLoading} onClick={() => handleAiReportClick('weekly')}>
              AI 周报
            </Button>
            <Button icon={<RobotOutlined />} loading={aiLoading} onClick={() => handleAiReportClick('sprint_review')}>
              AI 回顾
            </Button>
            <Button icon={<RobotOutlined />} loading={aiLoading} onClick={() => handleAiReportClick('project_summary')}>
              AI 总结
            </Button>
            <Button icon={<DownloadOutlined />} onClick={() => handleExport('csv')}>
              CSV
            </Button>
            <Button icon={<DownloadOutlined />} onClick={() => handleExport('pdf')}>
              PDF
            </Button>
          </Space>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Segmented
            value={reportType}
            onChange={(v) => setReportType(v as ReportType)}
            options={[
              { label: '日报', value: 'daily' },
              { label: '周报', value: 'weekly' },
              { label: 'Sprint 报告', value: 'sprint' },
            ]}
          />
          {reportType === 'daily' && (
            <DatePicker
              value={date}
              onChange={(d) => setDate(d || dayjs())}
            />
          )}
          {reportType === 'weekly' && (
            <DatePicker.RangePicker
              value={weekRange}
              onChange={(d) => {
                if (d) setWeekRange([d[0]!, d[1]!]);
              }}
            />
          )}
          {reportType === 'sprint' && (
            <Select
              placeholder="选择 Sprint"
              value={sprintId}
              onChange={setSprintId}
              style={{ width: 200 }}
              options={sprints.map((s) => ({
                label: `${s.name} (${s.status})`,
                value: s.id,
              }))}
            />
          )}
        </Space>

        <Spin spinning={loading}>
          {reportType === 'daily' && dailyReport && (
            <DailyReportView report={dailyReport} />
          )}
          {reportType === 'weekly' && weeklyReport && (
            <WeeklyReportView report={weeklyReport} />
          )}
          {reportType === 'sprint' && sprintReport && (
            <SprintReportView report={sprintReport} />
          )}
        </Spin>
      </Card>

      {/* 历史报告选择弹窗 */}
      <Modal
        title={`${AI_TYPE_LABELS[currentAiType] || 'AI 报告'} - 历史记录`}
        open={historyModalOpen}
        onCancel={() => setHistoryModalOpen(false)}
        width={600}
        footer={[
          <Button key="cancel" onClick={() => setHistoryModalOpen(false)}>取消</Button>,
          <Button key="regenerate" type="primary" icon={<ReloadOutlined />} onClick={() => doGenerate(currentAiType)}>
            重新生成
          </Button>,
        ]}
      >
        <Text type="secondary" style={{ marginBottom: 12, display: 'block' }}>
          该项目已有 {historyList.length} 条历史报告，您可以查看历史或重新生成。
        </Text>
        <List
          size="small"
          style={{ maxHeight: 320, overflow: 'auto' }}
          dataSource={historyList}
          renderItem={(item) => (
            <List.Item
              style={{ cursor: 'pointer', padding: '8px 12px', borderRadius: 6 }}
              onMouseEnter={(e) => (e.currentTarget.style.background = '#f5f5f5')}
              onMouseLeave={(e) => (e.currentTarget.style.background = '')}
              onClick={() => viewHistoryReport(item)}
              actions={[
                <Button key="view" size="small" type="link" icon={<EyeOutlined />} onClick={(e) => { e.stopPropagation(); viewHistoryReport(item); }}>
                  查看
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={<Text strong style={{ fontSize: 13 }}>{item.title}</Text>}
                description={
                  <Space>
                    <HistoryOutlined style={{ fontSize: 12, color: '#999' }} />
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {item.generatedAt ? dayjs(item.generatedAt).format('YYYY-MM-DD HH:mm') : '-'}
                    </Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Modal>

      {/* AI 报告查看 Modal */}
      <Modal
        title={aiTitle || 'AI 生成报告'}
        open={aiModalOpen}
        onCancel={() => setAiModalOpen(false)}
        width={860}
        footer={[
          <Button key="copy" icon={<CopyOutlined />} onClick={handleCopyMarkdown}>复制 Markdown</Button>,
          <Button key="download" icon={<FileTextOutlined />} onClick={handleDownloadMarkdown}>下载 .md</Button>,
          <Button key="close" type="primary" onClick={() => setAiModalOpen(false)}>关闭</Button>,
        ]}
      >
        <div
          className="ai-chat-bubble ai-chat-bubble--assistant"
          style={{ maxHeight: '62vh', overflow: 'auto', padding: 16, fontSize: 14 }}
        >
          {aiMarkdown ? (
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                a: ({ href, children }) => (
                  <Typography.Link href={href} target="_blank" rel="noopener noreferrer">
                    {children}
                  </Typography.Link>
                ),
                code: ({ className, children, ...props }: any) => {
                  const isBlock = className?.startsWith('language-');
                  if (isBlock) {
                    return (
                      <pre style={{ background: '#1e1e1e', color: '#d4d4d4', padding: '10px 12px', borderRadius: 6, overflowX: 'auto' }}>
                        <code className={className}>{children}</code>
                      </pre>
                    );
                  }
                  return <code {...props}>{children}</code>;
                },
              }}
            >
              {aiMarkdown}
            </ReactMarkdown>
          ) : (
            <Text type="secondary">生成中...</Text>
          )}
        </div>
      </Modal>
    </div>
  );
}
