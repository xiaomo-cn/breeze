import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import LinkExtension from '@tiptap/extension-link';
import Color from '@tiptap/extension-color';
import Highlight from '@tiptap/extension-highlight';
import ImageExtension from '@tiptap/extension-image';
import { TextStyle } from '@tiptap/extension-text-style';
import { Button, Space, message, Popover, Input, Upload } from 'antd';
import {
  BoldOutlined,
  ItalicOutlined,
  UnderlineOutlined,
  StrikethroughOutlined,
  OrderedListOutlined,
  UnorderedListOutlined,
  CodeOutlined,
  HighlightOutlined,
  FontColorsOutlined,
  LinkOutlined,
  PictureOutlined,
  BgColorsOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { useState, useEffect, useRef, useCallback } from 'react';
import { getUserSuggestions } from '../../api/users';
import { uploadAttachment, getDownloadUrl } from '../../api/attachments';
import type { User } from '../../types';

interface Props {
  taskId?: number;
  onSubmit: (content: string) => Promise<void>;
  placeholder?: string;
  initialContent?: string;
}

// 预设文字颜色
const TEXT_COLORS = [
  '#000000', '#434343', '#666666', '#999999', '#bfbfbf',
  '#cf1322', '#d4380d', '#d46b08', '#d48806', '#7cb305',
  '#389e0d', '#08979c', '#0958d9', '#531dab', '#c41d7f',
];

// 预设高亮/背景颜色
const HIGHLIGHT_COLORS = [
  '#ffd6e7', '#ffd6a5', '#fdffb6', '#caffbf', '#9bf6ff',
  '#a0c4ff', '#bdb2ff', '#ffc6ff', '#f0f0f0', '#ffffff',
  '#f4a261', '#e76f51', '#2a9d8f', '#264653', '#e9c46a',
  'transparent',
];

export default function CommentEditor({
  taskId,
  onSubmit,
  placeholder = '写下你的评论...',
  initialContent,
}: Props) {
  const [submitting, setSubmitting] = useState(false);
  const [suggestions, setSuggestions] = useState<User[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [mentionRange, setMentionRange] = useState<{ from: number; to: number } | null>(null);
  const [linkUrl, setLinkUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleImageUpload = useCallback(
    async (files: File[]) => {
      if (!taskId) {
        message.warning('无法上传图片：缺少任务上下文');
        return;
      }

      for (const file of files) {
        if (!file.type.startsWith('image/')) continue;
        if (file.size > 10 * 1024 * 1024) {
          message.warning(`图片 ${file.name} 超过 10MB 限制`);
          continue;
        }

        setUploading(true);
        try {
          const attachment = await uploadAttachment(taskId, file);
          const url = attachment.url ?? getDownloadUrl(attachment.id);
          editor?.chain().focus().setImage({ src: url, alt: file.name }).run();
        } catch {
          message.error(`上传 ${file.name} 失败`);
        } finally {
          setUploading(false);
        }
      }
    },
    [taskId],
  );

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
      }),
      Underline,
      LinkExtension.configure({
        openOnClick: false,
        HTMLAttributes: { class: 'comment-link' },
      }),
      TextStyle,
      Color,
      Highlight.configure({ multicolor: true }),
      ImageExtension.configure({
        HTMLAttributes: { class: 'comment-image' },
      }),
    ],
    content: initialContent || '',
    editorProps: {
      attributes: {
        class:
          'prose prose-sm max-w-none focus:outline-none min-h-[80px] p-3 border border-gray-300 rounded-lg focus:border-blue-400 focus:ring-1 focus:ring-blue-200',
      },
      handleDrop: (view, event) => {
        const files = Array.from(event.dataTransfer?.files || []);
        if (files.some((f) => f.type.startsWith('image/'))) {
          handleImageUpload(files);
          return true;
        }
        return false;
      },
      handlePaste: (view, event) => {
        const items = Array.from(event.clipboardData?.items || []);
        const imageFiles = items
          .filter((item) => item.type.startsWith('image/'))
          .map((item) => item.getAsFile())
          .filter(Boolean) as File[];
        if (imageFiles.length > 0) {
          handleImageUpload(imageFiles);
          return true;
        }
        return false;
      },
    },
  });

  // @提及 监听
  useEffect(() => {
    if (!editor) return;

    const handleTextChange = () => {
      const { state } = editor;
      const { selection } = state;
      const { $from } = selection;

      const textBefore = $from.parent.textBetween(
        Math.max(0, $from.parentOffset - 50),
        $from.parentOffset,
      );
      const match = textBefore.match(/@([^\s@]*)$/);

      if (match) {
        setShowSuggestions(true);
        const from = $from.pos - match[0].length;
        setMentionRange({ from, to: $from.pos });
        getUserSuggestions(match[1] || ' ').then(setSuggestions).catch(() => setSuggestions([]));
      } else {
        setShowSuggestions(false);
        setSuggestions([]);
      }
    };

    editor.on('update', handleTextChange);
    return () => {
      editor.off('update', handleTextChange);
    };
  }, [editor]);

  const insertMention = (user: User) => {
    if (!editor || !mentionRange) return;
    const displayName = user.displayName || user.username;
    editor
      .chain()
      .focus()
      .deleteRange({ from: mentionRange.from, to: mentionRange.to })
      .insertContent(`[@${displayName}](/users/${user.id})`)
      .run();
    setShowSuggestions(false);
    setSuggestions([]);
  };

  const handleSubmit = async () => {
    if (!editor) return;
    const text = editor.getText().trim();
    if (!text) {
      message.warning('请输入评论内容');
      return;
    }

    setSubmitting(true);
    try {
      const html = editor.getHTML();
      await onSubmit(html);
      editor.commands.clearContent();
    } finally {
      setSubmitting(false);
    }
  };

  const handleSetLink = () => {
    if (!editor) return;
    const previousUrl = editor.getAttributes('link').href;
    if (previousUrl) {
      editor.chain().focus().unsetLink().run();
      return;
    }
    const url = linkUrl || window.prompt('输入链接地址:');
    if (url) {
      editor
        .chain()
        .focus()
        .extendMarkRange('link')
        .setLink({ href: url.startsWith('http') ? url : `https://${url}` })
        .run();
      setLinkUrl('');
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length > 0) handleImageUpload(files);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  if (!editor) return null;

  const ToolBtn = ({
    icon,
    title,
    onClick,
    active,
  }: {
    icon: React.ReactNode;
    title: string;
    onClick: () => void;
    active?: boolean;
  }) => (
    <Button
      type="text"
      size="small"
      title={title}
      onClick={onClick}
      style={{
        color: active ? '#1677ff' : '#666',
        background: active ? '#e6f4ff' : 'transparent',
        minWidth: 28,
        height: 28,
        padding: '0 4px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {icon}
    </Button>
  );

  return (
    <div style={{ position: 'relative' }}>
      {/* 工具栏 */}
      <div
        style={{
          display: 'flex',
          gap: 2,
          marginBottom: 8,
          flexWrap: 'wrap',
          padding: '4px 6px',
          background: '#fafafa',
          borderRadius: 8,
          border: '1px solid #f0f0f0',
        }}
      >
        {/* 文字格式 */}
        <ToolBtn
          icon={<BoldOutlined />}
          title="加粗 (Ctrl+B)"
          onClick={() => editor.chain().focus().toggleBold().run()}
          active={editor.isActive('bold')}
        />
        <ToolBtn
          icon={<ItalicOutlined />}
          title="斜体 (Ctrl+I)"
          onClick={() => editor.chain().focus().toggleItalic().run()}
          active={editor.isActive('italic')}
        />
        <ToolBtn
          icon={<UnderlineOutlined />}
          title="下划线 (Ctrl+U)"
          onClick={() => editor.chain().focus().toggleUnderline().run()}
          active={editor.isActive('underline')}
        />
        <ToolBtn
          icon={<StrikethroughOutlined />}
          title="删除线"
          onClick={() => editor.chain().focus().toggleStrike().run()}
          active={editor.isActive('strike')}
        />

        <div style={{ width: 1, background: '#e8e8e8', margin: '2px 4px' }} />

        {/* 文字颜色 */}
        <Popover
          trigger="click"
          content={
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, width: 200 }}>
              {TEXT_COLORS.map((color) => (
                <div
                  key={color}
                  onClick={() => editor.chain().focus().setColor(color).run()}
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: 4,
                    background: color,
                    cursor: 'pointer',
                    border: color === '#ffffff' ? '1px solid #d9d9d9' : 'none',
                    boxShadow:
                      editor.isActive('textStyle', { color })
                        ? '0 0 0 2px #1677ff'
                        : undefined,
                  }}
                />
              ))}
              <div
                onClick={() => editor.chain().focus().unsetColor().run()}
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: 4,
                  cursor: 'pointer',
                  border: '1px solid #d9d9d9',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 10,
                  color: '#999',
                }}
              >
                ✕
              </div>
            </div>
          }
        >
          <ToolBtn
            icon={<FontColorsOutlined />}
            title="文字颜色"
            onClick={() => {}}
            active={editor.isActive('textStyle')}
          />
        </Popover>

        {/* 背景高亮 */}
        <Popover
          trigger="click"
          content={
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, width: 200 }}>
              {HIGHLIGHT_COLORS.map((color) => (
                <div
                  key={color}
                  onClick={() => {
                    if (color === 'transparent') {
                      editor.chain().focus().unsetHighlight().run();
                    } else {
                      editor.chain().focus().toggleHighlight({ color }).run();
                    }
                  }}
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: 4,
                    background: color === 'transparent' ? '#fff' : color,
                    cursor: 'pointer',
                    border:
                      color === 'transparent'
                        ? '1px dashed #d9d9d9'
                        : color === '#ffffff'
                          ? '1px solid #d9d9d9'
                          : 'none',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    color: '#999',
                  }}
                >
                  {color === 'transparent' ? '✕' : ''}
                </div>
              ))}
            </div>
          }
        >
          <ToolBtn
            icon={<HighlightOutlined />}
            title="背景高亮"
            onClick={() => {}}
            active={editor.isActive('highlight')}
          />
        </Popover>

        <div style={{ width: 1, background: '#e8e8e8', margin: '2px 4px' }} />

        {/* 列表 & 结构 */}
        <ToolBtn
          icon={<UnorderedListOutlined />}
          title="无序列表"
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          active={editor.isActive('bulletList')}
        />
        <ToolBtn
          icon={<OrderedListOutlined />}
          title="有序列表"
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          active={editor.isActive('orderedList')}
        />
        <ToolBtn
          icon={<CodeOutlined />}
          title="代码块"
          onClick={() => editor.chain().focus().toggleCodeBlock().run()}
          active={editor.isActive('codeBlock')}
        />
        <ToolBtn
          icon={<EyeOutlined />}
          title="引用块"
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
          active={editor.isActive('blockquote')}
        />

        <div style={{ width: 1, background: '#e8e8e8', margin: '2px 4px' }} />

        {/* 链接 */}
        <Popover
          trigger="click"
          content={
            <Space direction="vertical" style={{ width: 260 }}>
              <Input
                size="small"
                placeholder="https://..."
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                onPressEnter={handleSetLink}
              />
              <Space>
                <Button size="small" type="primary" onClick={handleSetLink}>
                  {editor.isActive('link') ? '更新链接' : '添加链接'}
                </Button>
                {editor.isActive('link') && (
                  <Button
                    size="small"
                    onClick={() => editor.chain().focus().unsetLink().run()}
                  >
                    移除
                  </Button>
                )}
              </Space>
            </Space>
          }
        >
          <ToolBtn
            icon={<LinkOutlined />}
            title="插入链接"
            onClick={() => {}}
            active={editor.isActive('link')}
          />
        </Popover>

        {/* 图片 */}
        <>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            style={{ display: 'none' }}
            onChange={handleFileSelect}
          />
          <ToolBtn
            icon={<PictureOutlined />}
            title="插入图片"
            onClick={() => fileInputRef.current?.click()}
            active={false}
          />
        </>
      </div>

      {/* 编辑区 */}
      <EditorContent editor={editor} />

      {/* @提及 建议列表 */}
      {showSuggestions && suggestions.length > 0 && (
        <div
          style={{
            position: 'absolute',
            bottom: 60,
            left: 0,
            background: '#fff',
            border: '1px solid #d9d9d9',
            borderRadius: 6,
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            maxHeight: 200,
            overflow: 'auto',
            zIndex: 1000,
            width: 240,
          }}
        >
          {suggestions.map((user) => (
            <div
              key={user.id}
              onClick={() => insertMention(user)}
              style={{
                padding: '6px 12px',
                cursor: 'pointer',
                fontSize: 13,
                borderBottom: '1px solid #f0f0f0',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = '#f5f5f5')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              {user.displayName || user.username}
              <span style={{ color: '#999', marginLeft: 8, fontSize: 12 }}>
                @{user.username}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* 提交按钮 */}
      <div style={{ marginTop: 8, textAlign: 'right' }}>
        <Space>
          {uploading && (
            <span style={{ color: '#999', fontSize: 12 }}>图片上传中...</span>
          )}
          <Button onClick={handleSubmit} type="primary" loading={submitting}>
            提交评论
          </Button>
        </Space>
      </div>
    </div>
  );
}
