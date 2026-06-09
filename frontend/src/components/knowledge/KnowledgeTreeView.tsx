import React, { useEffect, useState } from 'react';
import { Tree, App, Input, Modal, Tag, Tooltip } from 'antd';
import { FolderOutlined, FileOutlined, DeleteOutlined, ReloadOutlined, ExclamationCircleOutlined, LoadingOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { fetchFolderTree, searchDocuments, deleteDocument, retryEmbedding, moveDocument } from '../../api/knowledge';
import type { KnowledgeDocument } from '../../types/knowledge';
import type { DataNode } from 'antd/es/tree';

interface Props {
  folderId: number | null;
  onNavigate: (folderId: number | null) => void;
  onRefresh: () => void;
}

function EmbeddingTag({ status, fileType }: { status: string; fileType?: string }) {
  if (status === 'completed' || fileType === 'folder') return null;
  const config: Record<string, { color: string; icon: React.ReactNode }> = {
    pending:    { color: 'default', icon: <ClockCircleOutlined /> },
    processing: { color: 'processing', icon: <LoadingOutlined /> },
    failed:     { color: 'error', icon: <ExclamationCircleOutlined /> },
  };
  const c = config[status];
  if (!c) return null;
  return (
    <Tooltip title={status === 'failed' ? '索引失败，右键可重试' : status}>
      <Tag color={c.color} icon={c.icon} style={{ fontSize: 10, lineHeight: '14px', padding: '0 3px', marginLeft: 4 }}>
        {status === 'failed' ? '!' : ''}
      </Tag>
    </Tooltip>
  );
}

function docToNode(doc: KnowledgeDocument, allDocs: KnowledgeDocument[]): DataNode & { data: KnowledgeDocument } {
  const children = allDocs.filter((d) => d.parentFolderId === doc.id);
  return {
    key: `doc-${doc.id}`,
    title: (
      <span>
        {doc.title}
        <EmbeddingTag status={doc.embeddingStatus} fileType={doc.fileType} />
      </span>
    ) as unknown as string,  // Ant Design Tree title accepts ReactNode
    icon: doc.fileType === 'folder' ? <FolderOutlined /> : <FileOutlined />,
    isLeaf: doc.fileType !== 'folder' || children.length === 0,
    children: children.map((c) => docToNode(c, allDocs)),
    data: doc,
  } as DataNode & { data: KnowledgeDocument };
}

export default function KnowledgeTreeView({ folderId, onNavigate, onRefresh }: Props) {
  const [treeData, setTreeData] = useState<DataNode[]>([]);
  const { message } = App.useApp();

  useEffect(() => {
    loadTree();
  }, []);

  const loadTree = async () => {
    try {
      const docs = await fetchFolderTree();
      const roots = docs.filter((d) => d.parentFolderId === null);
      const tree = roots.map((r) => docToNode(r, docs));
      setTreeData(tree);
    } catch {
      message.error('加载文件夹树失败');
    }
  };

  const handleSelect = (keys: React.Key[]) => {
    if (keys.length > 0) {
      const key = String(keys[0]).replace('doc-', '');
      const id = Number(key);
      if (!isNaN(id)) {
        // 只有文件夹才导航，点击文件不影响 currentFolderId（否则切回网格视图会为空）
        const doc = findDocById(treeData, id);
        if (doc && doc.fileType === 'folder') {
          onNavigate(id);
        }
      }
    }
  };

  const handleDelete = async (docId: number, title: string) => {
    Modal.confirm({
      title: `确定要删除「${title}」吗？`,
      content: '删除后向量数据和文件将被清理，此操作不可撤销。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteDocument(docId);
          message.success('已删除');
          loadTree();
          onRefresh();
        } catch (err: any) {
          message.error(err?.response?.data?.message || '删除失败');
        }
      },
    });
  };

  const handleSearch = async (keyword: string) => {
    if (!keyword.trim()) { loadTree(); return; }
    try {
      const docs = await searchDocuments(null, keyword);
      // 构建搜索结果的树形展示
      const tree = docs
        .filter((d) => d.fileType === 'folder' || d.parentFolderId !== null)
        .map((d) => docToNode(d, docs));
      setTreeData(tree);
    } catch {
      message.error('搜索失败');
    }
  };

  const handleRetry = async (docId: number) => {
    try {
      await retryEmbedding(docId);
      message.success('已重新提交向量化');
      loadTree();
      onRefresh();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '重试失败');
    }
  };

  /** 拖拽文件到文件夹 */
  const handleDrop = async (info: any) => {
    const dragDoc = (info.dragNode as any).data as KnowledgeDocument;
    const dropDoc = (info.node as any).data as KnowledgeDocument;

    // 只有放入文件夹内部（非排序间隙）且目标是文件夹时才处理
    if (info.dropToGap) return;
    if (!dropDoc || dropDoc.fileType !== 'folder') return;
    if (dragDoc.parentFolderId === dropDoc.id) return; // 已在目标文件夹

    try {
      await moveDocument(dragDoc.id, dropDoc.id);
      message.success(`已将「${dragDoc.title}」移动到「${dropDoc.title}」`);
      loadTree();
      onRefresh();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '移动失败');
    }
  };

  // 找到被选中的节点信息
  const findDocById = (nodes: DataNode[], id: number): KnowledgeDocument | null => {
    for (const node of nodes) {
      if ((node as any).data?.id === id) return (node as any).data;
      if (node.children) {
        const found = findDocById(node.children, id);
        if (found) return found;
      }
    }
    return null;
  };

  const allNodes = treeData;
  const selectedDoc = folderId ? findDocById(allNodes, folderId) : null;

  return (
    <div style={{ padding: '8px 12px' }}>
      {treeData.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60, color: '#bfbfbf' }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>📭</div>
          <div>暂无文件夹，请先创建</div>
        </div>
      ) : (
        <>
          <Input.Search
            placeholder="搜索文件或文件夹…"
            allowClear
            size="small"
            style={{ marginBottom: 8 }}
            onSearch={handleSearch}
            onChange={(e) => { if (!e.target.value) loadTree(); }}
          />
          {/* 如果选中了索引失败的文件，显示重试按钮 */}
          {selectedDoc && selectedDoc.embeddingStatus === 'failed' && (
            <div style={{ marginBottom: 8, padding: '4px 8px', background: '#fff2f0', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 12 }}>
              <span>⚠️ <strong>{selectedDoc.title}</strong> 向量化失败</span>
              <a onClick={() => handleRetry(selectedDoc.id)} style={{ cursor: 'pointer', color: '#1677ff' }}>
                <ReloadOutlined /> 重试
              </a>
            </div>
          )}
          <Tree
            showIcon
            defaultExpandAll
            treeData={treeData}
            onSelect={handleSelect}
            draggable={(node) => {
              const doc = (node as any).data as KnowledgeDocument;
              return doc?.fileType !== 'folder'; // 只有文件可拖拽
            }}
            onDrop={handleDrop}
            titleRender={(node) => {
              const doc = (node as any).data as KnowledgeDocument;
              if (!doc) return node.title as React.ReactNode;
              return (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {doc.title}
                    <EmbeddingTag status={doc.embeddingStatus} fileType={doc.fileType} />
                  </span>
                  <DeleteOutlined
                    style={{ fontSize: 11, color: '#bfbfbf', marginLeft: 8, flexShrink: 0 }}
                    onClick={(e) => { e.stopPropagation(); handleDelete(doc.id, doc.title); }}
                  />
                </div>
              );
            }}
            style={{ fontSize: 13 }}
          />
        </>
      )}
    </div>
  );
}
