import { useEffect, useState } from 'react';
import { Upload, Button, List, Typography, message, Popconfirm, Spin, Empty } from 'antd';
import { UploadOutlined, DeleteOutlined, DownloadOutlined, FileOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd';
import { listAttachments, uploadAttachment, getDownloadUrl, deleteAttachment } from '../../api/attachments';
import type { Attachment } from '../../types';

const { Text } = Typography;

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface Props {
  taskId: number;
}

export default function AttachmentList({ taskId }: Props) {
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setAttachments(await listAttachments(taskId));
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [taskId]);

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      await uploadAttachment(taskId, file);
      message.success(`文件 ${file.name} 上传成功`);
      load();
    } catch {
      message.error('上传失败');
    } finally {
      setUploading(false);
    }
    return false; // prevent default upload
  };

  const handleDelete = async (id: number) => {
    await deleteAttachment(id);
    message.success('文件已删除');
    load();
  };

  if (loading) return <Spin style={{ display: 'block', textAlign: 'center', padding: 16 }} />;

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Upload
          beforeUpload={(file) => { handleUpload(file as File); return false; }}
          showUploadList={false}
          disabled={uploading}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>
            上传文件
          </Button>
        </Upload>
      </div>

      {attachments.length === 0 ? (
        <Empty description="暂无附件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <List
          dataSource={attachments}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button
                  key="download"
                  type="link"
                  size="small"
                  icon={<DownloadOutlined />}
                  href={getDownloadUrl(item)}
                />,
                <Popconfirm
                  key="delete"
                  title="确定删除此文件？"
                  onConfirm={() => handleDelete(item.id)}
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                avatar={<FileOutlined style={{ fontSize: 20 }} />}
                title={<Text style={{ fontSize: 13 }}>{item.fileName}</Text>}
                description={
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {formatSize(item.fileSize)} · {item.userName || '未知'} ·{' '}
                    {new Date(item.createdAt).toLocaleDateString('zh-CN')}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  );
}
