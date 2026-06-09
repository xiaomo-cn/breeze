## 1. 数据库迁移

- [x] 1.1 创建 Flyway V2 迁移脚本，新增 `knowledge_documents`（含 `parent_folder_id`、`file_hash`、`chunk_count`、`embedding_status`）、`knowledge_tags`、`knowledge_document_tags`（多对多关联）、`knowledge_document_permissions`、`knowledge_conversations`、`knowledge_messages` 表及索引

## 2. 后端：多模态 AI 配置

- [x] 2.1 在 `application.yml` 新增知识库配置项：Chat（base-url 指向百炼，model=qwen-vl-max）、Embedding（base-url 指向百炼，model=qwen3-vl-embedding）、splitter 参数（default-chunk-size, default-overlap, max-chunks-per-doc）
- [x] 2.2 创建 `KnowledgeAiConfig.java`，注册 `@Qualifier("knowledgeChatClient")` ChatClient bean 和 `@Qualifier("knowledgeEmbeddingModel")` EmbeddingModel bean，与现有 `@Primary` bean 并存

## 3. 后端：Entity 层

- [x] 3.1 创建 `KnowledgeDocument` 实体（id, title, description, fileName, fileType, fileSize, fileHash, storageKey, parentFolderId, chunkCount, embeddingStatus, createdBy, updatedBy, createdAt, updatedAt, isDeleted）
- [x] 3.2 创建 `KnowledgeTag` 实体（id, name, color）
- [x] 3.3 创建 `KnowledgeDocumentTag` 实体（documentId, tagId）
- [x] 3.4 创建 `KnowledgeDocumentPermission` 实体（id, documentId, userId, permission）
- [x] 3.5 创建 `KnowledgeConversation` 实体（id, userId, title, model, createdAt, updatedAt）
- [x] 3.6 创建 `KnowledgeMessage` 实体（id, conversationId, role, content, referencedDocs JSONB, tokenCount, createdAt）

## 4. 后端：Mapper 层

- [x] 4.1 创建 `KnowledgeDocumentMapper`：CRUD + 按 `parent_folder_id` 查询子项 + 标题关键词递归搜索（WITH RECURSIVE）+ 文件夹树查询
- [x] 4.2 创建 `KnowledgeTagMapper`：CRUD + 按名称模糊查找（自动补全）
- [x] 4.3 创建 `KnowledgeDocumentPermissionMapper`：按文档 ID + 用户 ID 查询、按文档 ID 批量查所有权限
- [x] 4.4 创建 `KnowledgeConversationMapper`：按用户 ID 分页查历史对话
- [x] 4.5 创建 `KnowledgeMessageMapper`：按对话 ID 查消息列表

## 5. 后端：Service 层

- [x] 5.1 创建 `KnowledgeDocumentService`：上传（校验大小/计算哈希/去重/保存文件/入库）、文件夹 CRUD、删除（清理文件+向量+子项检查）、当前文件夹内容查询、标题关键词递归搜索
- [x] 5.2 创建 `KnowledgeEmbeddingService`：`@Async` 执行 Spring AI ETL 管线（TikaDocumentReader → 按文件类型选择 Splitter → 调用 `qwen3-vl-embedding` 批量向量化 → 写入 `vector_store`），失败标记 `embedding_status='failed'`，支持重试
- [x] 5.3 创建 `KnowledgePermissionService`：权限继承逻辑（子项默认继承父文件夹权限，可单独覆盖）、权限校验
- [x] 5.4 创建 `KnowledgeRetrievalService`：将用户问题向量化 → 用 `doc_type` 过滤检索切片 → 按 `doc_id` 聚合去重 → 按用户权限过滤 → 返回 Top-K 文档（含页码/切片引用）
- [x] 5.5 创建 `KnowledgeChatService`：接收问题 → 检索相关文档 → 构建多模态消息（文字 + 原文件引用）→ 调用知识库 ChatClient → SSE 流式返回 → 保存对话和消息
- [x] 5.6 创建 `KnowledgeConversationService`：对话 CRUD、消息历史加载、自动标题生成

## 6. 后端：Controller 层

- [x] 6.1 创建 `KnowledgeDocumentController`：`POST /api/v1/knowledge/documents`（上传）、`POST /api/v1/knowledge/folders`（新建文件夹）、`GET /api/v1/knowledge/documents`（当前文件夹内容列表）、`GET /api/v1/knowledge/documents/tree`（完整文件夹树）、`GET /api/v1/knowledge/documents/{id}`（详情）、`PUT /api/v1/knowledge/documents/{id}`（更新标签/权限/移动）、`DELETE /api/v1/knowledge/documents/{id}`（删除）、`POST /api/v1/knowledge/documents/{id}/retry-embedding`（重试向量化）
- [x] 6.2 创建 `KnowledgeTagController`：`GET /api/v1/knowledge/tags`（标签列表+自动补全）
- [x] 6.3 创建 `KnowledgeChatController`：`POST /api/v1/knowledge/chat`（SSE 流式问答）、`GET /api/v1/knowledge/conversations`（历史对话列表）、`GET /api/v1/knowledge/conversations/{id}/messages`（消息历史）
- [x] 6.4 创建 `KnowledgeFileController`：`GET /api/v1/knowledge/files/{storageKey}`（文件预览/下载，校验权限）

## 7. 前端：类型与 API 层

- [x] 7.1 在 `types/` 中新增知识库相关 TypeScript 类型（KnowledgeDocument, KnowledgeFolder, KnowledgeTag, KnowledgeConversation, KnowledgeMessage 等）
- [x] 7.2 在 `api/` 中新增知识库 API 封装（uploadDocument, createFolder, fetchFolderContents, fetchFolderTree, deleteDocument, knowledgeChat SSE, fetchConversations 等）

## 8. 前端：组件

- [x] 8.1 创建 `KnowledgeUploadModal`：文件拖拽/选择、标题输入、标签选择（支持新建）、权限设置（继承父文件夹默认值）
- [x] 8.2 创建 `KnowledgeTreeView`：完整文件夹树组件，支持展开/折叠、点击导航、右键菜单（新建文件夹/上传/删除/重命名）
- [x] 8.3 创建 `KnowledgeGridView`：面包屑导航 + 文件/文件夹卡片网格，双击进入子文件夹，右键菜单
- [x] 8.4 创建 `KnowledgeChatPanel`：SSE 流式渲染、引用文档卡片（含页码/章节引用）、对话消息列表、输入框、历史对话侧栏
- [x] 8.5 创建 `KnowledgeBasePage`：左侧文档面板（工具栏 + 树/网格双视图切换） + 右侧 AI 问答面板，响应式适配

## 9. 前端：路由与侧栏

- [x] 9.1 在 `routes/` 中新增 `/knowledge` 路由，对应 `KnowledgeBasePage`
- [x] 9.2 在 `Sidebar` 组件中新增"知识库"导航项，位于"仪表盘"下方、"用户管理"上方，仅对登录用户可见

## 10. 集成验证

- [ ] 10.1 启动后端 + 前端，验证完整链路：创建文件夹 → 上传文档 → 自动切片向量化 → AI 问答引用原文件
- [ ] 10.2 验证双视图切换（网格 ↔ 树形），切换保持当前位置
- [ ] 10.3 验证文件哈希去重
- [ ] 10.4 验证权限继承（父文件夹权限 → 子项自动继承 → 单独覆盖）
- [ ] 10.5 验证搜索（标题关键词在当前目录及子目录递归搜索）
- [ ] 10.6 验证对话历史保存和恢复
