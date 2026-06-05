import { useEffect, useState } from 'react';
import { Button, Modal, Form, Input, InputNumber, ColorPicker, Select, message, Popconfirm, Tag, Typography, Empty } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, LockOutlined, HolderOutlined } from '@ant-design/icons';
import { getBoard, createColumn, updateColumn, deleteColumn, updateColumnsOrder } from '../../api/board';
import type { BoardData, ColumnData } from '../../types/board';
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import { SortableContext, useSortable, arrayMove, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

const { Text, Title } = Typography;

const PROTECTED_STATUSES = ['todo', 'in_progress', 'done'];

interface Props {
  projectId: number;
}

/** 可拖拽的列卡片行 */
function SortableColumnRow({
  col,
  isProtected,
  onEdit,
  onDelete,
  deleteTargetId,
  setDeleteTargetId,
  boardColumns,
}: {
  col: ColumnData;
  isProtected: boolean;
  onEdit: (c: ColumnData) => void;
  onDelete: (c: ColumnData) => void;
  deleteTargetId: number | null;
  setDeleteTargetId: (id: number | null) => void;
  boardColumns: ColumnData[];
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: col.id });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 16px',
    background: '#fafafa',
    borderRadius: 8,
    border: '1px solid #f0f0f0',
  };

  return (
    <div ref={setNodeRef} style={style}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div {...attributes} {...listeners} style={{ cursor: 'grab', color: '#999' }}>
          <HolderOutlined />
        </div>
        <div
          style={{
            width: 24,
            height: 24,
            borderRadius: 4,
            backgroundColor: col.color,
            border: '1px solid #d9d9d9',
            flexShrink: 0,
          }}
        />
        <div>
          <Text strong>{col.name}</Text>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            status: {col.statusMapping}
          </Text>
          {isProtected && (
            <Tag icon={<LockOutlined />} color="blue" style={{ marginLeft: 8 }}>
              核心列
            </Tag>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {col.wipLimit > 0 && <Tag>WIP: {col.wipLimit}</Tag>}
        <Text type="secondary" style={{ fontSize: 12 }}>
          排序: {col.sortOrder}
        </Text>
        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(col)}>
          编辑
        </Button>
        {!isProtected && (
          <Popconfirm
            title="删除列"
            description={
              <div style={{ width: 250 }}>
                <p>将此列下的任务迁移到：</p>
                <Select
                  style={{ width: '100%' }}
                  placeholder="选择目标列"
                  value={deleteTargetId}
                  onChange={setDeleteTargetId}
                  options={boardColumns
                    .filter((c) => c.id !== col.id)
                    .map((c) => ({
                      label: `${c.name} (${c.statusMapping})`,
                      value: c.id,
                    }))}
                />
              </div>
            }
            onConfirm={() => onDelete(col)}
            okText="删除"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        )}
      </div>
    </div>
  );
}

export default function BoardSettings({ projectId }: Props) {
  const [board, setBoard] = useState<BoardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingColumn, setEditingColumn] = useState<ColumnData | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const pointerSensor = useSensor(PointerSensor, {
    activationConstraint: { distance: 5 },
  });
  const sensors = useSensors(pointerSensor);

  const loadBoard = async () => {
    setLoading(true);
    try {
      const data = await getBoard(projectId);
      setBoard(data);
    } catch {
      message.error('加载看板配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadBoard(); }, [projectId]);

  const openAdd = () => {
    setEditingColumn(null);
    form.resetFields();
    form.setFieldsValue({ wipLimit: 0, color: '#808080' });
    setModalOpen(true);
  };

  const openEdit = (col: ColumnData) => {
    setEditingColumn(col);
    form.setFieldsValue(col);
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      if (editingColumn) {
        await updateColumn(projectId, editingColumn.id, values);
        message.success('列已更新');
      } else {
        await createColumn(projectId, values);
        message.success('列已创建');
      }
      setModalOpen(false);
      loadBoard();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const handleDelete = async (col: ColumnData) => {
    if (!deleteTargetId) return;
    try {
      await deleteColumn(projectId, col.id, deleteTargetId);
      message.success('列已删除');
      setDeleteTargetId(null);
      loadBoard();
    } catch (e: any) {
      message.error(e.response?.data?.message || '删除失败');
    }
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || !board || active.id === over.id) return;

    const oldIndex = board.columns.findIndex((c) => c.id === active.id);
    const newIndex = board.columns.findIndex((c) => c.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = arrayMove(board.columns, oldIndex, newIndex);
    const sorted = reordered.map((col, idx) => ({ ...col, sortOrder: idx }));
    setBoard({ ...board, columns: sorted });

    try {
      await updateColumnsOrder(projectId, sorted.map((c) => ({ id: c.id, sortOrder: c.sortOrder })));
    } catch {
      message.error('排序更新失败');
      loadBoard();
    }
  };

  const isProtected = (col: ColumnData) => PROTECTED_STATUSES.includes(col.statusMapping);

  if (loading) return <div style={{ padding: 16, textAlign: 'center' }}>加载中...</div>;
  if (!board) return <Empty description="加载失败" />;

  const statusOptions = [
    { value: 'backlog', label: '待规划 (backlog)' },
    { value: 'todo', label: '待处理 (todo)' },
    { value: 'in_progress', label: '进行中 (in_progress)' },
    { value: 'review', label: '评审中 (review)' },
    { value: 'testing', label: '测试中 (testing)' },
    { value: 'done', label: '已完成 (done)' },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={5} style={{ margin: 0 }}>看板列管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
          新增列
        </Button>
      </div>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={board.columns.map((c) => c.id)} strategy={verticalListSortingStrategy}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {board.columns.map((col) => (
              <SortableColumnRow
                key={col.id}
                col={col}
                isProtected={isProtected(col)}
                onEdit={openEdit}
                onDelete={handleDelete}
                deleteTargetId={deleteTargetId}
                setDeleteTargetId={setDeleteTargetId}
                boardColumns={board.columns}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      {/* Add / Edit Modal */}
      <Modal
        title={editingColumn ? '编辑列' : '新增列'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="列名称"
            rules={[{ required: true, message: '请输入列名称' }]}
          >
            <Input placeholder="如：阻塞中" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="statusMapping"
            label="状态映射"
            rules={[{ required: true, message: '请输入状态映射值' }]}
            help="对应 tasks.status 的值，自定义输入或选择预设值"
          >
            <Select
              mode="tags"
              maxCount={1}
              placeholder="选择或输入状态值"
              options={statusOptions}
              disabled={editingColumn ? isProtected(editingColumn) : false}
            />
          </Form.Item>
          <Form.Item
            name="color"
            label="列颜色"
            rules={[{ required: true, message: '请选择颜色' }]}
          >
            <ColorPicker
              format="hex"
              presets={[
                { label: '推荐', colors: ['#94a3b8', '#e2e8f0', '#bfdbfe', '#fef08a', '#fce7f3', '#bbf7d0'] },
                { label: '更多', colors: ['#f87171', '#fb923c', '#fbbf24', '#a3e635', '#34d399', '#22d3ee', '#818cf8', '#a78bfa', '#f472b6'] },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="wipLimit"
            label="WIP 限制"
            help="0 表示不限制"
          >
            <InputNumber min={0} max={999} style={{ width: '100%' }} />
          </Form.Item>
          {editingColumn && (
            <Form.Item name="sortOrder" label="排序位置">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
}
