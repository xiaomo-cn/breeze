## MODIFIED Requirements

### Requirement: 摄入管线：DocumentParser 提取 + TokenTextSplitter 自适应切片
文档上传成功后，系统 SHALL 使用 `DocumentParser` 提取文本，将全文存入 `knowledge_documents.extracted_text`，然后使用 Spring AI 内置分块器按文件类型自适应切片。每个切片调用 `text-embedding-v4`（阿里百炼）生成 1024 维向量，写入 `vector_store`。

#### Scenario: PDF 文件切片
- **WHEN** 一个 PDF 文档上传完成
- **THEN** 系统使用 `TokenTextSplitter`（chunk_size=500, overlap=50）切片，全文存入 `extracted_text` 列，每个切片写入 `vector_store`，metadata 包含 `{doc_id, doc_type: "knowledge_document", chunk_index, page_number}`

#### Scenario: Markdown 文件切片
- **WHEN** 一个 Markdown 文档上传完成
- **THEN** 系统使用 `ParagraphSplitter` 按段落边界切片，全文存入 `extracted_text` 列，每个切片写入 `vector_store`

#### Scenario: HTML 文件切片
- **WHEN** 一个 HTML 文档上传完成
- **THEN** 系统使用 `ContentPatternSplitter` 按 DOM 结构切片

#### Scenario: 其他文件类型切片
- **WHEN** 一个 docx、xlsx、pptx 或 csv 文档上传完成
- **THEN** 系统使用 `TokenTextSplitter` 切片，全文存入 `extracted_text` 列

#### Scenario: 切片数超限
- **WHEN** 文档切片数超过 `max-chunks-per-doc`（默认 100）
- **THEN** 系统记录警告日志，截断至 100 片，文档标记为"部分索引"

#### Scenario: 切片参数从配置读取
- **WHEN** 修改 `application.yml` 中 `spring.ai.knowledge.splitter.default-chunk-size` 为 800
- **THEN** 后续文档切片使用 800 token 作为 chunk 大小

### Requirement: 切片级别向量存储
每个切片 SHALL 作为一条独立记录写入 `vector_store`，metadata 包含 `doc_id`、`doc_type`、`chunk_index`、`page_number`。原始文件原样保存在 `FileStorageService`。问答时使用 `extracted_text` 字段中的全文，不再重复解析原始文件。

#### Scenario: 多切片文档检索
- **WHEN** 用户提问与某 50 页 PDF 的第 42 页内容相关
- **THEN** 系统通过切片级检索定位到第 42 页对应的切片，返回原文件引用（含页码）和 `extracted_text` 全文

#### Scenario: 切片索引成功
- **WHEN** 所有切片成功写入 `vector_store`
- **THEN** 文档 `embedding_status` 更新为 `completed`，`chunk_count` 设为切片总数

#### Scenario: 历史文档 extracted_text 为空时回退
- **WHEN** 检索到的文档 `extracted_text` 为 NULL（历史数据）
- **THEN** 问答时回退到通过 `DocumentParser` 从文件存储重新解析

### Requirement: Embedding 失败处理
当 `text-embedding-v4` API 调用失败时，系统 SHALL 记录错误并标记文档状态，支持手动重试。

#### Scenario: 切片向量化失败
- **WHEN** 某个切片的 Embedding API 调用失败（超时或返回错误）
- **THEN** 文档 `embedding_status` 标记为 `failed`，记录错误日志

#### Scenario: 手动重试向量化
- **WHEN** 用户对 `embedding_status = 'failed'` 的文档点击"重试索引"
- **THEN** 系统重新执行该文档的摄入管线，清空旧的切片向量，重新填充 `extracted_text`

### Requirement: 文档删除时清理向量
文档删除时，系统 SHALL 同步删除 `vector_store` 中 `metadata.doc_id` 等于该文档 ID 的所有切片条目。

#### Scenario: 删除文档时清理所有切片
- **WHEN** 用户删除文档
- **THEN** `vector_store` 中该文档的所有切片条目被删除

### Requirement: 检索时按文档类型过滤
知识库检索时，系统 SHALL 仅查询 `metadata.doc_type = "knowledge_document"` 的向量条目，与项目任务向量隔离。检索结果 SHALL 按 `doc_id` 聚合，去重后按用户权限过滤。

#### Scenario: 检索仅返回知识库文档切片
- **WHEN** 用户进行知识库问答检索（Top-K=30 切片）
- **THEN** 检索结果仅包含 `doc_type = "knowledge_document"` 的切片，按 `doc_id` 聚合去重，返回 Top-5 文档

#### Scenario: 按权限过滤检索结果
- **WHEN** 检索到的文档中部分文档用户无权限
- **THEN** 无权限文档被过滤，仅返回用户可访问的文档
