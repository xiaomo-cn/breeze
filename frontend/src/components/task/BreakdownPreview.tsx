import { useState, useEffect } from 'react';
import { Modal, List, Button, Select, InputNumber, Space, message, Typography, Spin, Popconfirm } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { confirmBreakdown, type SubtaskNode } from '../../api/ai';

const { Text } = Typography;

interface Props {
  taskId: number;
  jsonText: string;
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

interface SubtaskItem {
  key: string;
  title: string;
  type: string;
  priority: string;
  estimatedHours: number;
}

/**
 * 从 AI 返回的 JSON 文本中提取子任务数组。
 */
function parseSubtasks(text: string): SubtaskNode[] {
  try {
    const match = text.match(/\[[\s\S]*\]/);
    if (match) return JSON.parse(match[0]);
    return [];
  } catch {
    return [];
  }
}

export default function BreakdownPreview({ taskId, jsonText, open, onClose, onCreated }: Props) {
  const [items, setItems] = useState<SubtaskItem[]>([]);
  const [confirming, setConfirming] = useState(false);

  // 当 jsonText 变化时，重新解析并更新列表
  useEffect(() => {
    if (jsonText) {
      const nodes = parseSubtasks(jsonText);
      setItems(
        nodes.map((node, i) => ({
          key: `${i}`,
          title: node.title,
          type: node.type || 'subtask',
          priority: node.priority || 'medium',
          estimatedHours: node.estimatedHours || 0,
        })),
      );
    }
  }, [jsonText]);

  // 兜底：弹窗打开且 jsonText 已就绪但 items 为空时重新解析
  useEffect(() => {
    if (open && jsonText && items.length === 0) {
      const nodes = parseSubtasks(jsonText);
      if (nodes.length > 0) {
        setItems(
          nodes.map((node, i) => ({
            key: `${i}`,
            title: node.title,
            type: node.type || 'subtask',
            priority: node.priority || 'medium',
            estimatedHours: node.estimatedHours || 0,
          })),
        );
      }
    }
  }, [open, jsonText, items.length]);

  const handleUpdate = (key: string, field: string, value: unknown) => {
    setItems((prev) =>
      prev.map((item) => (item.key === key ? { ...item, [field]: value } : item)),
    );
  };

  const handleDelete = (key: string) => {
    setItems((prev) => prev.filter((item) => item.key !== key));
  };

  const handleConfirm = async () => {
    if (items.length === 0) {
      message.warning('没有可创建的子任务');
      return;
    }
    setConfirming(true);
    try {
      await confirmBreakdown(taskId, items.map((item) => ({
        title: item.title,
        type: item.type,
        priority: item.priority,
        estimatedHours: item.estimatedHours,
      })));
      message.success(`已创建 ${items.length} 个子任务`);
      onCreated();
      onClose();
    } catch {
      message.error('创建失败');
    } finally {
      setConfirming(false);
    }
  };

  return (
    <Modal
      title={`AI 任务拆解预览（${items.length} 项）`}
      open={open}
      onCancel={onClose}
      width={700}
      onOk={handleConfirm}
      confirmLoading={confirming}
      okText="确认创建"
      cancelText="取消"
      destroyOnClose
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
        AI 已生成以下子任务，可编辑类型、优先级和预估工时，不需要的项目可删除。
      </Text>
      {items.length > 0 ? (
        <List
          dataSource={items}
          renderItem={(item) => (
            <List.Item
              style={{ padding: '8px 0' }}
              actions={[
                <Popconfirm
                  key="delete"
                  title="确定删除此子任务？"
                  onConfirm={() => handleDelete(item.key)}
                >
                  <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <Space size="small" wrap>
                <span style={{ fontWeight: 500, minWidth: 120 }}>{item.title}</span>
                <Select
                  size="small"
                  value={item.type}
                  style={{ width: 80 }}
                  onChange={(val) => handleUpdate(item.key, 'type', val)}
                  options={[
                    { label: '任务', value: 'task' },
                    { label: '子任务', value: 'subtask' },
                    { label: 'Bug', value: 'bug' },
                  ]}
                />
                <Select
                  size="small"
                  value={item.priority}
                  style={{ width: 80 }}
                  onChange={(val) => handleUpdate(item.key, 'priority', val)}
                  options={[
                    { label: '低', value: 'low' },
                    { label: '中', value: 'medium' },
                    { label: '高', value: 'high' },
                    { label: '紧急', value: 'urgent' },
                  ]}
                />
                <InputNumber
                  size="small"
                  value={item.estimatedHours}
                  min={0}
                  style={{ width: 70 }}
                  placeholder="工时"
                  onChange={(val) => handleUpdate(item.key, 'estimatedHours', val ?? 0)}
                />
              </Space>
            </List.Item>
          )}
        />
      ) : (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
          <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
            正在解析 AI 返回的子任务结构...
          </Text>
        </div>
      )}
    </Modal>
  );
}
