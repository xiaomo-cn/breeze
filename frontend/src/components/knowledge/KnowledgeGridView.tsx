import { useEffect, useState } from 'react';
import { Breadcrumb, Dropdown, App, Modal, Tag, Tooltip } from 'antd';
import { FileOutlined, FolderOutlined, DeleteOutlined, ReloadOutlined, LoadingOutlined, CheckCircleOutlined, ExclamationCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { DndContext, DragOverlay, useDraggable, useDroppable, PointerSensor, useSensor, useSensors, type DragStartEvent, type DragEndEvent } from '@dnd-kit/core';
import { fetchFolderContents, deleteDocument, retryEmbedding, moveDocument } from '../../api/knowledge';
import type { KnowledgeDocument } from '../../types/knowledge';

interface Props {
  folderId: number | null;
  breadcrumb: { id: number | null; title: string }[];
  onNavigate: (folderId: number | null, breadcrumb?: { id: number | null; title: string }[]) => void;
  onBreadcrumbChange: (breadcrumb: { id: number | null; title: string }[]) => void;
  onRefresh: () => void;
}

/** 向量化状态徽标 */
function EmbeddingBadge({ status, fileType }: { status: string; fileType?: string }) {
  if (status === 'completed' || fileType === 'folder') return null; // 文件夹不需要索引标识
  const config: Record<string, { color: string; icon: React.ReactNode; text: string }> = {
    pending:    { color: 'default', icon: <ClockCircleOutlined />, text: '待索引' },
    processing: { color: 'processing', icon: <LoadingOutlined />, text: '索引中' },
    failed:     { color: 'error', icon: <ExclamationCircleOutlined />, text: '索引失败' },
  };
  const c = config[status];
  if (!c) return null;
  return (
    <Tooltip title={status === 'failed' ? '向量化失败，可右键重试' : c.text}>
      <Tag color={c.color} icon={c.icon} style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}>
        {c.text}
      </Tag>
    </Tooltip>
  );
}

/** 可拖拽的文件卡片包装 */
function DraggableCard({ item, children }: { item: KnowledgeDocument; children: React.ReactNode }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `grid-${item.id}`,
    data: item,
    disabled: item.fileType === 'folder', // 文件夹不可拖拽
  });
  const style: React.CSSProperties = transform ? {
    transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
    opacity: isDragging ? 0.4 : 1,
  } : {};
  return (
    <div ref={setNodeRef} style={style} {...listeners} {...attributes}>
      {children}
    </div>
  );
}

/** 可接收文件的文件夹卡片包装 */
function DroppableCard({ item, children }: { item: KnowledgeDocument; children: React.ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({
    id: `drop-${item.id}`,
    data: item,
    disabled: item.fileType !== 'folder', // 只有文件夹可接收
  });
  return (
    <div
      ref={setNodeRef}
      style={{
        borderRadius: 8,
        outline: isOver ? '2px dashed #1677ff' : 'none',
        outlineOffset: -2,
        background: isOver ? '#e6f4ff' : undefined,
      }}
    >
      {children}
    </div>
  );
}

export default function KnowledgeGridView({ folderId, breadcrumb, onNavigate, onBreadcrumbChange, onRefresh }: Props) {
  const [items, setItems] = useState<KnowledgeDocument[]>([]);
  const [activeItem, setActiveItem] = useState<KnowledgeDocument | null>(null);
  const { message } = App.useApp();

  const pointerSensor = useSensor(PointerSensor, { activationConstraint: { distance: 5 } });
  const sensors = useSensors(pointerSensor);

  useEffect(() => {
    loadFolder(folderId);
  }, [folderId]);

  const loadFolder = async (id: number | null) => {
    try {
      const data = await fetchFolderContents(id);
      setItems(data);
      // 根目录时重置面包屑
      if (id === null) {
        onBreadcrumbChange([{ id: null, title: '📚 知识库' }]);
      }
    } catch {
      message.error('加载失败');
    }
  };

  const handleDoubleClick = (item: KnowledgeDocument) => {
    if (item.fileType === 'folder') {
      const newBreadcrumb = [...breadcrumb, { id: item.id, title: item.title }];
      onBreadcrumbChange(newBreadcrumb);
      onNavigate(item.id, newBreadcrumb);
    }
  };

  const handleBreadcrumbClick = (index: number) => {
    const target = breadcrumb[index];
    const newBreadcrumb = breadcrumb.slice(0, index + 1);
    onBreadcrumbChange(newBreadcrumb);
    onNavigate(target.id, newBreadcrumb);
  };

  const handleDelete = async (item: KnowledgeDocument) => {
    Modal.confirm({
      title: item.fileType === 'folder' ? `确定要删除文件夹「${item.title}」吗？` : `确定要删除「${item.title}」吗？`,
      content: '删除后向量数据和文件将被清理，此操作不可撤销。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteDocument(item.id);
          message.success('已删除');
          loadFolder(folderId);
          onRefresh();
        } catch (err: any) {
          message.error(err?.response?.data?.message || '删除失败');
        }
      },
    });
  };

  const handleRetry = async (item: KnowledgeDocument) => {
    try {
      await retryEmbedding(item.id);
      message.success('已重新提交向量化');
      loadFolder(folderId);
      onRefresh();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '重试失败');
    }
  };

  const handleDragStart = (event: DragStartEvent) => {
    const item = event.active.data.current as KnowledgeDocument;
    setActiveItem(item);
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    setActiveItem(null);
    const { active, over } = event;
    if (!over) return;

    const draggedItem = active.data.current as KnowledgeDocument;
    const dropTarget = over.data.current as KnowledgeDocument;

    // 只有文件可以拖到文件夹中
    if (draggedItem.fileType === 'folder') return;
    if (!dropTarget || dropTarget.fileType !== 'folder') return;
    if (draggedItem.parentFolderId === dropTarget.id) return; // 已在目标文件夹

    try {
      await moveDocument(draggedItem.id, dropTarget.id);
      message.success(`已将「${draggedItem.title}」移动到「${dropTarget.title}」`);
      loadFolder(folderId);
      onRefresh();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '移动失败');
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1048576).toFixed(1)} MB`;
  };

  const formatTime = (ts: string) => {
    const d = new Date(ts);
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`;
    return `${Math.floor(diff / 604800000)}周前`;
  };

  return (
    <div style={{ padding: '8px 12px' }}>
      <Breadcrumb
        style={{ marginBottom: 10, fontSize: 12 }}
        items={breadcrumb.map((b, i) => ({
          title: (
            <a onClick={() => handleBreadcrumbClick(i)}
               style={{ fontWeight: i === breadcrumb.length - 1 ? 600 : 400 }}>
              {b.title}
            </a>
          ),
        }))}
      />

      {items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60, color: '#bfbfbf' }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>📭</div>
          <div>此文件夹为空</div>
        </div>
      ) : (
        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))', gap: 8 }}>
            {items.map((item) => (
              <DroppableCard key={item.id} item={item}>
                <DraggableCard item={item}>
                  <Dropdown trigger={['contextMenu']} menu={{
                    items: [
                      ...(item.fileType === 'folder' ? [{ key: 'open', label: '打开', icon: <FolderOutlined /> }] : []),
                      ...(item.embeddingStatus === 'failed' ? [{ key: 'retry', label: '重新索引', icon: <ReloadOutlined /> }] : []),
                      { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'delete') handleDelete(item);
                      if (key === 'open') handleDoubleClick(item);
                      if (key === 'retry') handleRetry(item);
                    },
                  }}>
                    <div
                      onDoubleClick={() => handleDoubleClick(item)}
                      style={{
                        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                        padding: '14px 8px 10px', borderRadius: 8, cursor: 'pointer',
                        border: '1px solid transparent', textAlign: 'center', position: 'relative',
                        transition: 'all .15s',
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.background = '#fafafa'; e.currentTarget.style.borderColor = '#f0f0f0'; }}
                      onMouseLeave={(e) => { e.currentTarget.style.background = ''; e.currentTarget.style.borderColor = 'transparent'; }}
                    >
                      {/* 向量化状态角标 */}
                      <div style={{ position: 'absolute', top: 2, right: 4 }}>
                        <EmbeddingBadge status={item.embeddingStatus} fileType={item.fileType} />
                      </div>
                      <div style={{ fontSize: 34 }}>
                        {item.fileType === 'folder' ? '📁'
                          : item.fileType === 'pdf' ? '📄'
                          : item.fileType === 'md' ? '📝'
                          : item.fileType === 'docx' ? '📘'
                          : item.fileType === 'png' || item.fileType === 'jpg' ? '🖼️'
                          : '📎'}
                      </div>
                      <div style={{ fontSize: 12, lineHeight: 1.4, wordBreak: 'break-all', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {item.title}
                      </div>
                      <div style={{ fontSize: 11, color: '#bfbfbf' }}>
                        {item.fileType === 'folder' ? `${item.childCount ?? 0} 项` : `${formatSize(item.fileSize)} · ${formatTime(item.createdAt)}`}
                      </div>
                    </div>
                  </Dropdown>
                </DraggableCard>
              </DroppableCard>
            ))}
          </div>
          <DragOverlay dropAnimation={null}>
            {activeItem ? (
              <div style={{
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                padding: '14px 8px 10px', borderRadius: 8, background: '#fff',
                border: '1px solid #d9d9d9', boxShadow: '0 4px 12px rgba(0,0,0,.12)',
                width: 130, textAlign: 'center',
              }}>
                <div style={{ fontSize: 34 }}>
                  {activeItem.fileType === 'pdf' ? '📄' : activeItem.fileType === 'md' ? '📝' : activeItem.fileType === 'docx' ? '📘' : activeItem.fileType === 'png' || activeItem.fileType === 'jpg' ? '🖼️' : '📎'}
                </div>
                <div style={{ fontSize: 12, lineHeight: 1.4, wordBreak: 'break-all' }}>{activeItem.title}</div>
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}
    </div>
  );
}
