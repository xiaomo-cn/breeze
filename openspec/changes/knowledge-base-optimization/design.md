## Context

知识库模块基于 `knowledge-base` change 实现，当前状态：43/49 任务完成，核心 CRUD、向量化、RAG 问答流程已跑通。但存在以下待改进的技术问题：

- **成本**：使用多模态向量模型 `tongyi-embedding-vision-plus`，但实际流程通过 Tika 提取纯文本后向量化，图片内容从未进入向量，多模态能力未利用却产生额外成本
- **代码耦合**：`KnowledgeEmbeddingService` 和 `KnowledgeChatService` 两处直接调用 `TIKA.parseToString()`，且问答时重复读文件 + 重复解析（上传时已解析过一次）
- **依赖版本**：Spring AI `1.0.0-M6`（2024 年里程碑版），依赖 Spring Milestones 仓库。内置 `DocumentSplitter` 类路径不兼容，只能用自定义滑动窗口分块
- **类型安全**：`JsonbTypeHandler` 声明为 `BaseTypeHandler<Map>`，但 `KnowledgeMessage.referencedDocs` 是 `List<Map>`，读操作有 `ClassCastException` 风险
- **前端体验**：对话历史未渲染、侧边栏高亮错误、缺少删除确认、文件预览 Content-Type 错误、SSE 无重试

**约束**：
- Spring Boot 3.3.5 不动（避免全栈升级风险）
- PostgreSQL 16 + pgvector 不动
- 复用主项目 `ChatClient`（DeepSeek）不动
- 知识库独立对话表不动

## Goals / Non-Goals

**Goals:**
1. 向量模型切换到 `text-embedding-v4`，降低 embedding 成本
2. 封装 `DocumentParser` 接口，为未来 OCR + POI 混合解析做准备
3. Spring AI 升级到 `1.1.7`，用 `TokenTextSplitter` 等内置分块器替换自定义实现
4. 消除问答时重复 Tika 调用（`extracted_text` 列）
5. 修复 `JsonbTypeHandler` 类型安全问题
6. 前端未完成代码补全 + 功能缺口修复

**Non-Goals:**
- 不升级 Spring Boot（保持 3.3.5）
- 不切换到多模态问答模型（DeepSeek 文本 RAG 可行且成本低）
- 不实现批量操作和回收站（后续独立 change）
- 不实现文档在线编辑
- 不修改知识库核心数据模型（仅新增 `extracted_text` 列）

## Decisions

### 1. Spring AI 版本选择：1.1.7

| 选项 | 评估 | 结论 |
|------|------|------|
| 1.0.8 | OSS 支持 2026-06 到期 | ❌ 即将 EOL |
| 1.1.7 | 最新稳定版，企业支持到 2032，Spring Boot 3.x 兼容 | ✅ 选择 |
| 2.0.0-RC1 | 需 Spring Boot 4.0 + Spring Framework 7.0 + Jackson 3 | ❌ 全栈升级，风险过大 |

1.1.7 已发布到 Maven Central，不需要 Spring Milestones 仓库。API 从 M6 到 1.1.7 跨度较大，编译后需逐一适配。

### 2. 文档解析封装：接口 + 单实现

```
DocumentParser (接口)
  └── TikaDocumentParser (当前实现，包装 Tika)
       未来：HybridDocumentParser (POI for docx/xlsx/pptx + Tika fallback)
```

`DocumentParser` 接口定义 `String parse(InputStream, String fileType, String fileName)`，在 `KnowledgeAiConfig` 中注册 Bean。不引入策略模式或工厂——当前只有一个实现，过度设计反而增加复杂度。

### 3. 消除重复 Tika 调用：extracted_text 字段

```
当前流程：
  上传 → Tika 解析① → 切块 → 向量化 → vector_store
  问答 → 读文件 → Tika 解析② → 截断 → Prompt  ← 重复！

优化后：
  上传 → Tika 解析① → 全文存 DB → 切块 → 向量化 → vector_store
  问答 → 读 DB extracted_text → 截断 → Prompt  ← 无重复！
```

在 `knowledge_documents` 表新增 `extracted_text TEXT` 列（Flyway V2_1）。历史数据为 NULL 不受影响，重新触发 embedding 可补全。

### 4. 分块策略：按文件类型自适应

| 文件类型 | 分块器 | 原因 |
|----------|--------|------|
| md, txt | `ParagraphSplitter` | 按段落自然边界切分 |
| pdf, docx, xlsx, pptx, csv | `TokenTextSplitter`(500 tokens, 50 overlap) | 基于 token 均匀切分 |
| html | `ContentPatternSplitter` | 按 HTML 语义结构切分 |
| default | `TokenTextSplitter` | 通用兜底 |

参数从 `application.yml` 的 `spring.ai.knowledge.splitter.*` 读取，与现有配置兼容。

### 5. 动态 Token 预算截断

当前：固定 2000 字符/篇，5 篇 = 最多 10K 字符。

优化后：总预算 32K token（约 128K 字符，DeepSeek 128K 上下文的 1/4），按检索到的文档数均分。单篇上限 8000 字符，防止超长文档挤占其他文档空间。

### 6. 前端对话历史 UI

在 `KnowledgeChatPanel` 左侧添加历史对话列表（Ant Design `Menu` 内嵌），点击切换。不引入 Drawer——知识库页面已有左右分栏布局，加一层 Drawer 会让交互变复杂。移除未使用的 `showHistory` 状态。

### 7. 文件预览 Content-Type 映射

```java
Map<String, String> MIME_TYPES = Map.of(
    "pdf", "application/pdf",
    "png", "image/png",
    "jpg", "image/jpeg",
    "jpeg", "image/jpeg",
    "gif", "image/gif",
    "md", "text/plain",
    "txt", "text/plain",
    "html", "text/html",
    "csv", "text/csv"
);
// 未匹配 → application/octet-stream（下载）
```

## Risks / Trade-offs

| 风险 | 可能性 | 缓解措施 |
|------|--------|----------|
| Spring AI 1.1.7 API 与 M6 不兼容导致编译错误多 | 高 | 逐文件编译修复，涉及 `AiConfig.java`、`KnowledgeAiConfig.java`、`KnowledgeEmbeddingService.java`；主 AI 模块的 ChatClient / @Tool 注解也需验证 |
| text-embedding-v4 维度不是 1024 | 低 | 官方文档确认为 1024，若不匹配需 ALTER TABLE 改 vector 维度并重建索引 |
| extracted_text 历史数据为空 | 低 | 新字段可 NULL，历史文档需重新触发 embedding 补全；问答时若字段为空则回退到 Tika 解析 |
| TokenTextSplitter 分块效果与自定义逻辑不同 | 中 | 保留配置入口（chunk-size, overlap），必要时可调整参数；中文文本两者差异通常不大 |
| 1.1.7 无 Spring Milestones 仓库后其他 Milestone 依赖不可用 | 低 | 检查 pom.xml 中是否有其他依赖也走 Milestones 仓库，确认后再移除 |

## Migration Plan

1. **配置变更**：修改 `application.yml` 模型名 → 重启生效，无数据迁移
2. **数据库**：Flyway V2_1 自动执行 `ALTER TABLE knowledge_documents ADD COLUMN extracted_text TEXT`，向下兼容
3. **Spring AI 升级**：先在 `pom.xml` 升级版本 → 编译 → 修复报错 → 单元测试
4. **回滚**：切换回旧 embedding 模型需重建向量（维度相同理论不需重建）；代码回滚通过 git revert
