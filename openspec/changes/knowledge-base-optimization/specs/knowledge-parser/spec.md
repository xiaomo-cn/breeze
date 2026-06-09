## ADDED Requirements

### Requirement: 文档解析器接口
系统 SHALL 定义 `DocumentParser` 接口，封装文档文本提取逻辑。接口提供 `String parse(InputStream inputStream, String fileType, String fileName)` 方法，根据文件类型和输入流返回提取的纯文本内容。

#### Scenario: 接口定义
- **WHEN** 开发者需要添加新的文档解析实现（如 OCR、POI）
- **THEN** 实现 `DocumentParser` 接口即可注入到 `KnowledgeEmbeddingService` 和 `KnowledgeChatService`，无需修改业务代码

### Requirement: TikaDocumentParser 实现
系统 SHALL 提供 `TikaDocumentParser` 作为 `DocumentParser` 的默认实现，内部包装 Apache Tika 2.x 的 `parseToString()` 方法，支持 PDF、DOCX、XLSX、PPTX、MD、TXT、HTML、CSV、PNG、JPG 等文件类型的文本提取。

#### Scenario: PDF 文本提取
- **WHEN** `TikaDocumentParser.parse(inputStream, "pdf", "doc.pdf")` 被调用
- **THEN** 返回 PDF 中提取的纯文本内容

#### Scenario: 图片文件文本提取
- **WHEN** `TikaDocumentParser.parse(inputStream, "png", "img.png")` 被调用
- **THEN** 返回 Tika 可提取的文本（如 PNG 中的文字若 Tika 不支持则可能返回空字符串），不抛出异常

#### Scenario: 解析失败处理
- **WHEN** Tika 解析过程抛出异常
- **THEN** `TikaDocumentParser` 记录 WARN 级别日志，返回 null

### Requirement: DocumentParser Bean 注册
`TikaDocumentParser` SHALL 在 `KnowledgeAiConfig` 中注册为 Spring Bean，`KnowledgeEmbeddingService` 和 `KnowledgeChatService` 通过构造函数注入 `DocumentParser` 接口。

#### Scenario: 依赖注入
- **WHEN** Spring 容器启动
- **THEN** `KnowledgeEmbeddingService` 和 `KnowledgeChatService` 均获得 `TikaDocumentParser` 实例
