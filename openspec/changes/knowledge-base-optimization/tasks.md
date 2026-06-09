## 1. 基础调整（低风险，不改变行为）

- [x] 1.1 修改 `application.yml`：`spring.ai.knowledge.embedding.options.model` 默认值从 `tongyi-embedding-vision-plus-2026-03-06` 改为 `text-embedding-v4`
- [x] 1.2 同步调整环境变量默认值：`EMBEDDING_MODEL`、`KNOWLEDGE_EMBEDDING_MODEL` 默认值
- [x] 1.3 创建 `DocumentParser` 接口（`cn.xiaomo.breeze.knowledge.parser.DocumentParser`）
- [x] 1.4 创建 `TikaDocumentParser` 实现，迁移现有 Tika 逻辑，添加文件类型判断日志
- [x] 1.5 `KnowledgeEmbeddingService`：注入 `DocumentParser`，替换 `extractText()` 中的直接 Tika 调用
- [x] 1.6 `KnowledgeChatService`：注入 `DocumentParser`，替换 `extractDocContent()` 中的直接 Tika 调用
- [x] 1.7 在 `KnowledgeAiConfig` 中注册 `TikaDocumentParser` Bean（通过 `@Component` 自动注册）
- [x] 1.8 修复 `JsonbTypeHandler`：泛型从 `Map<String, Object>` 改为 `Object`，反序列化时根据 JSON 首字符判断类型
- [x] 1.9 验证 `AiMessage.metadata` 等其他 JSONB 字段的兼容性（全部为 `Map<String, Object>`，无影响）

## 2. Spring AI 升级（1.0.0-M6 → 1.1.7）

- [x] 2.1 修改 `pom.xml`：`<spring-ai.version>` 从 `1.0.0-M6` 升级到 `1.1.7`
- [x] 2.2 移除 `pom.xml` 中 Spring Milestones 仓库（1.1.7 在 Maven Central）
- [x] 2.3 修改 `pom.xml` 依赖坐标：`spring-ai-openai-spring-boot-starter` → `spring-ai-starter-model-openai`，`spring-ai-pgvector-store` → `spring-ai-starter-vector-store-pgvector`
- [x] 2.4 编译通过，API 向后兼容，无需修改 `KnowledgeAiConfig.java`、`AiConfig.java`
- [x] 2.5 验证主 AI 模块 12 个 `@Tool` 注解方法兼容性（编译通过，无报错）

## 3. TokenTextSplitter 替换自定义分块

- [x] 3.1 在 `KnowledgeEmbeddingService` 中创建 `TokenTextSplitter`（chunk_size=500, max_chunks=100）
- [x] 3.2 ~~ParagraphSplitter / ContentPatternSplitter~~ Spring AI 1.1.7 仅提供 TokenTextSplitter，统一使用
- [x] 3.3 删除自定义 `splitText()` 和 `estimatePage()` 方法
- [x] 3.4 删除不再使用的 `defaultOverlap` 配置字段
- [x] 3.5 编译验证通过

## 4. 流程优化

- [x] 4.1 创建 Flyway 迁移 `V2_1__knowledge_extracted_text.sql`：`ALTER TABLE knowledge_documents ADD COLUMN extracted_text TEXT`
- [x] 4.2 `KnowledgeDocument` 实体新增 `extractedText` 字段
- [x] 4.3 Mapper XML 使用 `SELECT *`，自动包含新列
- [x] 4.4 `KnowledgeEmbeddingService`：上传后 Tika 提取全文 → 写入 `extracted_text` → 再切块向量化
- [x] 4.5 `KnowledgeChatService`：问答时从 `doc.getExtractedText()` 读取全文（为 NULL 时回退到 DocumentParser + FileStorageService）
- [x] 4.6 实现动态 token 预算截断：总预算 32K token，均分到文档，单篇上限 8000 字符
- [x] 4.7 `KnowledgeRetrievalService`：返回 `RetrievalResult`（document + score），聚合时记录每篇文档的最高相似度分数
- [x] 4.8 `KnowledgeRetrievalService`：过滤掉最高分 < 0.3 的文档，全部不相关时返回空列表
- [x] 4.9 检索结果附带相似度分数，前端引用卡片展示（如"相关度 85%"）

## 5. 前端修复

- [x] 5.1 `Sidebar.tsx`：`getSelectedKeys()` 添加 `/knowledge` 和 `/admin/users` 路径匹配
- [x] 5.2 `KnowledgeChatPanel.tsx`：添加对话历史下拉菜单（Dropdown），点击切换历史对话
- [x] 5.3 `KnowledgeChatPanel.tsx`：移除未使用的 `showHistory` 状态
- [x] 5.4 `KnowledgeGridView.tsx`：删除操作添加 `Modal.confirm` 二次确认弹窗
- [x] 5.5 `KnowledgeTreeView.tsx`：删除操作添加 `Modal.confirm` 二次确认弹窗 + titleRender 删除按钮
- [x] 5.6 `KnowledgeTreeView.tsx`：上方添加搜索框（Input.Search），接入 `searchDocuments` API
- [x] 5.7 `types/knowledge.ts`：`ReferencedDoc` 新增 `score` 相似度分数字段

## 6. 功能补全

- [x] 6.1 `KnowledgeFileController.java`：根据 `fileType` 返回正确的 Content-Type（pdf→application/pdf, 图片→image/*, md/txt→text/plain, html→text/html）
- [x] 6.2 `KnowledgeChatPanel.tsx`：SSE 流读取添加断线重试逻辑（最多 3 次，递增延迟）

## 7. 回归验证

- [ ] 7.1 端到端验证：上传文档 → 自动嵌入（确认 extracted_text 已填充）→ 知识库问答引用正确文档
- [ ] 7.2 验证新旧 embedding 模型切换后检索质量无明显下降
- [ ] 7.3 验证 TokenTextSplitter 分块效果（chunk 数量、内容完整性）
- [ ] 7.4 验证主 AI 模块（ChatClient、@Tool 方法）升级后功能正常
- [ ] 7.5 验证对话历史切换、侧边栏高亮、文件预览、删除确认等前端功能
