## Why

知识库模块核心功能已开发完成（43/49 任务），但存在成本偏高（多模态向量模型）、代码质量（Tika 解析耦合、类型安全问题）、基础设施过时（Spring AI 1.0.0-M6）以及前端体验不完整等问题。本次优化在不改变知识库核心架构的前提下，系统性解决上述问题，使模块达到可上线状态。

## What Changes

- **向量模型降本**：从多模态 `tongyi-embedding-vision-plus` 切换到纯文本 `text-embedding-v4`（阿里百炼），维度保持 1024 不变，仅改配置
- **文档解析封装**：创建 `DocumentParser` 接口 + `TikaDocumentParser` 实现，消除 `KnowledgeEmbeddingService` 和 `KnowledgeChatService` 中对 Tika API 的直接耦合，为未来 OCR + POI 混合解析做准备
- **Spring AI 升级**：`1.0.0-M6` → `1.1.7`（Maven Central），自定义滑动窗口分块替换为内置 `TokenTextSplitter` + `ParagraphSplitter` + `ContentPatternSplitter` 按文件类型自适应分块。**BREAKING**：涉及 `AiConfig.java`、`KnowledgeAiConfig.java` 中 API 适配
- **消除重复解析**：新增 `knowledge_documents.extracted_text` 列，上传时存入 Tika 提取的全文，问答时直接读 DB 而非重新读文件 + Tika 解析
- **动态截断**：Prompt 中文档内容从固定 2000 字符改为按总 token 预算（32K）动态分配
- **相关性阈值**：检索时过滤 cosine < 0.3 的文档，全部不相关时告知用户
- **类型安全修复**：`JsonbTypeHandler` 泛型 `Map` → `Object`，兼容 `List<Map>` 的 JSONB 字段
- **前端完善**：对话历史列表 UI、侧边栏 `/knowledge` 高亮修复、删除确认弹窗、树视图搜索框、文件预览 Content-Type 修复、SSE 断线重试

## Capabilities

### New Capabilities
<!-- 本次优化是对现有知识库模块的增量改进，不引入新的独立能力 -->
- `knowledge-parser`: 文档解析器抽象——定义 `DocumentParser` 接口，提供 Tika 实现，预留 OCR + POI 混合解析扩展点

### Modified Capabilities
<!-- 以下能力属于 knowledge-base change，本次优化修改其行为和规格 -->
- `knowledge-embedding`: 向量模型从多模态切换到纯文本；分块策略从自定义滑动窗口改为 Spring AI TokenTextSplitter 自适应分块；上传后全文存入 extracted_text
- `knowledge-ai-chat`: 问答时从 DB 读取 extracted_text 而非重新解析文件；截断策略从固定 2000 字符改为动态 token 预算；添加相关性阈值过滤和分数展示
- `knowledge-document-management`: 新增 extracted_text 字段；文件预览支持正确的 Content-Type

## Impact

- **后端配置**：`application.yml` 修改模型名和分块参数
- **后端依赖**：`pom.xml` Spring AI 版本升级，移除 Spring Milestones 仓库
- **后端代码**：`KnowledgeAiConfig.java`、`KnowledgeEmbeddingService.java`、`KnowledgeChatService.java`、`KnowledgeRetrievalService.java`、`KnowledgeFileController.java`、`JsonbTypeHandler.java`、`AiConfig.java`
- **新增文件**：`DocumentParser.java`、`TikaDocumentParser.java`、`V2_1__knowledge_extracted_text.sql`
- **前端代码**：`KnowledgeChatPanel.tsx`、`Sidebar.tsx`、`KnowledgeGridView.tsx`、`KnowledgeTreeView.tsx`
- **数据库**：`knowledge_documents` 新增 `extracted_text TEXT` 列
