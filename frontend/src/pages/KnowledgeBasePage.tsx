import { useState, useCallback, useEffect, useRef } from 'react';
import { Button, Segmented, Modal, Input, App } from 'antd';
import { FolderAddOutlined, UploadOutlined, AppstoreOutlined, UnorderedListOutlined } from '@ant-design/icons';
import KnowledgeGridView from '../components/knowledge/KnowledgeGridView';
import KnowledgeTreeView from '../components/knowledge/KnowledgeTreeView';
import KnowledgeUploadModal from '../components/knowledge/KnowledgeUploadModal';
import KnowledgeChatPanel from '../components/knowledge/KnowledgeChatPanel';
import { createFolder } from '../api/knowledge';

type ViewMode = 'grid' | 'tree';

export default function KnowledgeBasePage() {
  const [viewMode, setViewMode] = useState<ViewMode>('grid');
  const [uploadVisible, setUploadVisible] = useState(false);
  const [folderModalVisible, setFolderModalVisible] = useState(false);
  const [folderName, setFolderName] = useState('');
  const [currentFolderId, setCurrentFolderId] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  // 面包屑提升到页面级，切换视图时不丢失
  const [breadcrumb, setBreadcrumb] = useState<{ id: number | null; title: string }[]>([
    { id: null, title: '📚 知识库' },
  ]);
  const { message } = App.useApp();

  // ===== 左右面板拖拽调整大小 =====
  const [leftWidth, setLeftWidth] = useState(460);
  const [isResizing, setIsResizing] = useState(false);
  const resizeStartRef = useRef({ x: 0, width: 0 });
  const MIN_LEFT = 280;
  const MIN_RIGHT = 320;

  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
    resizeStartRef.current = { x: e.clientX, width: leftWidth };
  }, [leftWidth]);

  useEffect(() => {
    if (!isResizing) return;
    const handleMouseMove = (e: MouseEvent) => {
      const dx = e.clientX - resizeStartRef.current.x;
      const newWidth = resizeStartRef.current.width + dx;
      // 限制最小/最大宽度：右面板至少保留 MIN_RIGHT
      const maxLeft = window.innerWidth - MIN_RIGHT;
      setLeftWidth(Math.max(MIN_LEFT, Math.min(newWidth, maxLeft)));
    };
    const handleMouseUp = () => setIsResizing(false);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing]);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);
  const navigateToFolder = useCallback((folderId: number | null, newBreadcrumb?: { id: number | null; title: string }[]) => {
    setCurrentFolderId(folderId);
    if (newBreadcrumb) setBreadcrumb(newBreadcrumb);
  }, []);

  const handleCreateFolder = async () => {
    if (!folderName.trim()) return;
    try {
      await createFolder(currentFolderId, folderName.trim());
      message.success('文件夹已创建');
      setFolderModalVisible(false);
      setFolderName('');
      refresh();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '创建失败');
    }
  };

  return (
    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', background: '#f5f5f5' }}>
      {/* 左侧：文档管理 */}
      <div style={{ width: leftWidth, minWidth: MIN_LEFT, background: '#fff', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
        {/* 工具栏 */}
        <div style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', gap: 6 }}>
          <Button type="primary" size="small" icon={<FolderAddOutlined />}
            onClick={() => setFolderModalVisible(true)}>
            新建文件夹
          </Button>
          <Button type="primary" size="small" icon={<UploadOutlined />}
            onClick={() => setUploadVisible(true)}>
            上传文件
          </Button>
          <span style={{ flex: 1 }} />
          <Segmented
            size="small"
            value={viewMode}
            onChange={(v) => setViewMode(v as ViewMode)}
            options={[
              { value: 'grid', icon: <AppstoreOutlined /> },
              { value: 'tree', icon: <UnorderedListOutlined /> },
            ]}
          />
        </div>

        {/* 视图内容 */}
        <div style={{ flex: 1, overflow: 'auto' }}>
          {viewMode === 'grid' ? (
            <KnowledgeGridView
              key={`grid-${refreshKey}`}
              folderId={currentFolderId}
              breadcrumb={breadcrumb}
              onNavigate={navigateToFolder}
              onBreadcrumbChange={setBreadcrumb}
              onRefresh={refresh}
            />
          ) : (
            <KnowledgeTreeView
              key={`tree-${refreshKey}`}
              folderId={currentFolderId}
              onNavigate={navigateToFolder}
              onRefresh={refresh}
            />
          )}
        </div>
      </div>

      {/* 可拖拽分割线 */}
      <div
        onMouseDown={handleResizeStart}
        style={{
          width: 5, cursor: 'col-resize', background: isResizing ? '#1677ff' : 'transparent',
          transition: isResizing ? 'none' : 'background .2s',
          flexShrink: 0, zIndex: 10,
        }}
        onMouseEnter={(e) => { if (!isResizing) e.currentTarget.style.background = '#e6f4ff'; }}
        onMouseLeave={(e) => { if (!isResizing) e.currentTarget.style.background = 'transparent'; }}
      />

      {/* 右侧：AI 问答 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: MIN_RIGHT, overflow: 'hidden' }}>
        <KnowledgeChatPanel />
      </div>

      {/* 上传文件弹窗 */}
      <KnowledgeUploadModal
        open={uploadVisible}
        parentFolderId={currentFolderId}
        onClose={() => setUploadVisible(false)}
        onSuccess={() => { setUploadVisible(false); refresh(); }}
      />

      {/* 新建文件夹弹窗 */}
      <Modal title="新建文件夹" open={folderModalVisible}
        onOk={handleCreateFolder} onCancel={() => { setFolderModalVisible(false); setFolderName(''); }}
        okText="创建" cancelText="取消"
        confirmLoading={false}>
        <Input placeholder="输入文件夹名称" value={folderName}
          onChange={(e) => setFolderName(e.target.value)}
          onPressEnter={handleCreateFolder}
          autoFocus />
      </Modal>
    </div>
  );
}
