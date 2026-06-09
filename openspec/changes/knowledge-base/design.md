## Context

Breeze 当前 AI 助手的 RAG 上下文仅包含项目内任务、Sprint、用户任务数据。团队需要一个知识库来存储和查询技术文档、产品需求、流程规范等企业级知识资产。

本设计采用 **Spring AI ETL 管线 + 多模态 LLM 问答** 的混合架构：
- **摄入侧**：使用 Spring AI 标准 ETL 管线（`DocumentReader` → `DocumentSplitter` → `VectorStore`），按文件类型自适应选择切片策略，参数从配置文件读取。切片用于检索定位，不用于最终答案
- **问答侧**：使用 `qwen-vl-max` 多模态大模型直接看原文件回答，图表/表格/排版 100% 保留

### 约束
- 文件存储复用现有 `FileStorageService`（Local / S3 / MinIO）
- 向量存储复用现有 `vector_store` 表（pgvector + HNSW）
- 与现有 DeepSeek + `text-embedding-v3` 管线并存，不互相干扰
- 前端遵循现有 Ant Design 组件体系

## Goals / Non-Goals

**Goals:**
- 提供组织级知识库，独立于项目上下文
- 文件夹自由层级管理（类似操作系统，不限深度），权限子项默认继承父文件夹
- 双视图模式（树形 / 网格），一键切换
- 支持上传常见企业文档格式（PDF、Word、PPT、Excel、Markdown、PNG、HTML、CSV、TXT）
- 多模态 AI 问答：直接看原文件回答，保留图表/表格/排版信息
- 文档级权限：read（只读）与 manage（管理）两类
- 标签管理：多标签，跨文件夹的横向分类
- 文件哈希去重（SHA-256）
- 独立对话历史，不影响项目 AI 对话

**Non-Goals:**
- 不做在线文档编辑器 / 富文本 Wiki
- 不做 OCR（多模态模型直接读图）
- 不做语义去重（首期只做哈希去重）
- 不做外部系统同步（Confluence / 飞书等）

## Decisions

### 1. 摄入管线：Spring AI ETL + 多模态问答

**选择**：Spring AI 标准 ETL 管线做切片检索，多模态 LLM 看原文件回答

```
文件上传
  → TikaDocumentReader 提取文本（仅用于切片，不用于最终答案）
  → 按文件类型自适应选择 Splitter 切片
  → 每个切片 → qwen3-vl-embedding → vector_store
  → metadata: { doc_id, doc_type: "knowledge_document", chunk_index, page_number }

问答：
  用户提问 → qwen3-vl-embedding → vector_store 检索切片
  → 按 doc_id 聚合 → 映射回原文件和对应页码
  → 提问 + 原文件 → qwen-vl-max（多模态）→ SSE 流式回答
```

**理由**：
- Tika 提取文本**只用于切片和检索**，质量不需要完美，能搜到就行
- 切片级 Embedding 保证检索精度（50 页 PDF 的第 42 页也能定位）
- Spring AI 内置 ETL 管线，开箱即用，不用自己造轮子
- `qwen-vl-max` 看原文件回答，图表、表格、排版信息 100% 保留

### 2. 切片策略：代码自适应 + 配置文件参数

**选择**：策略按文件类型自适应（代码中），参数从配置文件读取

```yaml
# application.yml
spring.ai.knowledge:
  splitter:
    default-chunk-size: 500       # token
    default-overlap: 50           # token
    max-chunks-per-doc: 100
```

```java
// KnowledgeEmbeddingService 中按文件类型选择 Splitter
switch (fileType) {
    case "md", "txt"   → ParagraphSplitter;         // 按段落边界
    case "pdf", "docx" → TokenTextSplitter;         // 按 token 数
    case "html"        → ContentPatternSplitter;    // 按 DOM 结构
    case "csv", "xlsx" → TokenTextSplitter;         // 按 token 数
    default            → TokenTextSplitter;         // 默认
}
```

**理由**：
- 策略选择不应该从外部配置（每种文件类型的结构特征是固定的），但 chunk_size / overlap 应该可调
- Spring AI 内置多种 `DocumentSplitter` 实现，开箱即用

### 3. 文件夹设计

**选择**：`parent_folder_id` 自引用，不限层级深度

```sql
ALTER TABLE knowledge_documents ADD COLUMN parent_folder_id BIGINT;
-- NULL 表示根目录下的文件
```

文件夹本身是一条 `knowledge_documents` 记录（`file_type = 'folder'`，`file_size = 0`）。

**权限继承**：子文件夹/文件默认继承父文件夹权限（可单独覆盖），上传时无需反复配置。

**前端双视图**：
- **网格模式**（默认）：面包屑导航 + 文件/文件夹卡片网格，双击进入子文件夹，类似 macOS Finder / Windows 资源管理器
- **树形模式**：完整层级树，展开/折叠，点击节点定位，类似 IDE 文件树
- 工具栏右侧 ⊞/☰ 切换按钮

### 4. 两个 ChatClient / EmbeddingModel Bean 并存

**选择**：在 Spring 容器中注册独立的 bean，通过 `@Qualifier` 区分

```java
// 现有（不动）
@Bean @Primary  ChatClient deepseekChatClient(...)
@Bean @Primary  EmbeddingModel textEmbeddingModel(...)

// 新增（知识库专用，百炼多模态）
@Bean @Qualifier("knowledgeChatClient")  ChatClient knowledgeChatClient(...)
@Bean @Qualifier("knowledgeEmbeddingModel")  EmbeddingModel knowledgeEmbeddingModel(...)
```

### 5. 复用 vector_store 表

**选择**：复用现有 `vector_store`，通过 `metadata.doc_type` 区分

```json
// 任务向量（现有）
{"task_id": 123, "project_id": 5}

// 知识库切片（新增）
{"doc_id": 42, "doc_type": "knowledge_document", "chunk_index": 3, "page_number": 5}
```

### 6. 对话系统：独立表

**选择**：新建 `knowledge_conversations` + `knowledge_messages` 表，与项目 AI 对话完全隔离

## Risks / Trade-offs

- **[风险] 多模态 Embedding 对大文件的切片处理** → 单文件最多切 100 片（可配置），超大文件建议用户拆分后上传
- **[风险] 百炼 API 的稳定性和延迟** → Chat 和 Embedding 均走百炼，若异常则知识库问答不可用。项目 AI 助手（DeepSeek）不受影响
- **[风险] Tika 提取文本的格式保真度** → 表格、代码块等可能丢失，但提取的文本**只用于切片检索**，不影响最终答案质量（最终答案由多模态 LLM 看原文件生成）
- **[风险] 文件夹权限继承的复杂性** → 子项权限变更时需要递归更新，写入时通过异步队列处理
