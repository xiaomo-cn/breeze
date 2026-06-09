import { useState } from 'react';
import { Modal, Form, Input, Upload, Select, App } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { uploadDocument, fetchTags } from '../../api/knowledge';

const { Dragger } = Upload;

interface Props {
  open: boolean;
  parentFolderId: number | null;
  onClose: () => void;
  onSuccess: () => void;
}

export default function KnowledgeUploadModal({ open, parentFolderId, onClose, onSuccess }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const { message } = App.useApp();

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (!file) { message.warning('请选择文件'); return; }
      setLoading(true);
      const formData = new FormData();
      formData.append('file', file);
      if (parentFolderId) formData.append('parentFolderId', String(parentFolderId));
      if (values.title) formData.append('title', values.title);
      if (values.description) formData.append('description', values.description);
      formData.append('defaultPermission', values.defaultPermission || 'everyone');
      if (values.tags?.length) {
        values.tags.forEach((t: string) => formData.append('tags', t));
      }
      await uploadDocument(formData);
      message.success('上传成功，正在处理向量化...');
      form.resetFields();
      setFile(null);
      onSuccess();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '上传失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title="上传文档" open={open} onOk={handleOk} onCancel={onClose}
      confirmLoading={loading} okText="确认上传" cancelText="取消" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item label="文件" required>
          <Dragger maxCount={1} beforeUpload={(f) => {
            setFile(f);
            // 自动用文件名（去扩展名）填充标题
            const currentTitle = form.getFieldValue('title');
            if (!currentTitle) {
              form.setFieldsValue({ title: f.name.replace(/\.[^/.]+$/, '') });
            }
            return false;
          }}
            onRemove={() => { setFile(null); form.setFieldsValue({ title: '' }); }}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p style={{ fontSize: 13 }}>点击或拖拽文件到此区域上传</p>
            <p style={{ fontSize: 11, color: '#8c8c8c' }}>支持 PDF、Word、PPT、Excel、Markdown、PNG、HTML、CSV</p>
          </Dragger>
        </Form.Item>
        <Form.Item name="title" label="文档标题">
          <Input placeholder="默认使用文件名，可自定义" />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea placeholder="简要描述文档内容（可选）" rows={2} />
        </Form.Item>
        <Form.Item name="tags" label="标签">
          <Select mode="tags" placeholder="输入标签，回车添加"
            filterOption onSearch={fetchTags}
            options={[]} />
        </Form.Item>
        <Form.Item name="defaultPermission" label="默认权限" initialValue="everyone">
          <Select options={[
            { value: 'everyone', label: '所有人可读' },
            { value: 'only_me', label: '仅自己可见' },
            { value: 'custom', label: '自定义（上传后设置）' },
          ]} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
