import { Button, List, Typography, Popconfirm } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { Conversation } from '../../api/ai';

const { Text } = Typography;

interface ConversationSidebarProps {
  conversations: Conversation[];
  activeId?: number;
  onSelect: (conv: Conversation) => void;
  onDelete: (convId: number) => void;
  onClose: () => void;
}

/**
 * 对话历史侧边栏。
 *
 * 显示会话列表，支持切换对话和删除。
 */
export default function ConversationSidebar({
  conversations,
  activeId,
  onSelect,
  onDelete,
  onClose,
}: ConversationSidebarProps) {
  return (
    <div style={{
      marginBottom: 12,
      borderBottom: '1px solid #f0f0f0',
      paddingBottom: 8,
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
      }}>
        <Text strong style={{ fontSize: 13 }}>对话历史</Text>
        <Button size="small" type="text" onClick={onClose}>收起</Button>
      </div>

      {conversations.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>暂无历史对话</Text>
      ) : (
        <List
          size="small"
          style={{ maxHeight: 160, overflow: 'auto' }}
          dataSource={conversations}
          renderItem={(conv) => (
            <List.Item
              style={{
                cursor: 'pointer',
                padding: '4px 8px',
                borderRadius: 4,
                background: conv.id === activeId ? '#e6f4ff' : undefined,
              }}
              onClick={() => onSelect(conv)}
              actions={[
                <Popconfirm
                  key="del"
                  title="确认删除?"
                  onConfirm={(e) => {
                    e?.stopPropagation();
                    onDelete(conv.id);
                  }}
                >
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={(e) => e.stopPropagation()}
                  />
                </Popconfirm>,
              ]}
            >
              <div>
                <Text ellipsis style={{ fontSize: 12, maxWidth: 260 }}>{conv.title}</Text>
                <div style={{ display: 'flex', gap: 8 }}>
                  {conv.messageCount != null && conv.messageCount > 0 && (
                    <Text type="secondary" style={{ fontSize: 10 }}>
                      {conv.messageCount} 条消息
                    </Text>
                  )}
                  {conv.lastMessage && (
                    <Text type="secondary" ellipsis style={{ fontSize: 10, maxWidth: 180 }}>
                      {conv.lastMessage.slice(0, 30)}
                      {conv.lastMessage.length > 30 ? '...' : ''}
                    </Text>
                  )}
                </div>
              </div>
            </List.Item>
          )}
        />
      )}
    </div>
  );
}
