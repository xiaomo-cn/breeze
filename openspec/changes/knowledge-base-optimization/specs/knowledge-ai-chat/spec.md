## MODIFIED Requirements

### Requirement: 文本 RAG 知识问答
系统 SHALL 提供基于 DeepSeek 文本模型的 RAG 知识库问答能力。用户提问后，系统通过 `text-embedding-v4` 检索相关文档切片，按 `doc_id` 聚合为文档列表，从 `knowledge_documents.extracted_text` 读取文档全文（历史数据为空时回退到 `DocumentParser` 解析），构建 RAG Prompt 发送给 DeepSeek ChatClient，生成回答并通过 SSE 流式推送到前端。

#### Scenario: 用户提问并收到回答
- **WHEN** 用户在知识库 AI 面板输入问题并发送
- **THEN** 系统检索 Top-5 相关文档（cosine ≥ 0.3），从 `extracted_text` 读取全文构建 Prompt，DeepSeek SSE 流式返回回答

#### Scenario: 无相关文档时
- **WHEN** 用户提问但检索到的文档相似度均低于阈值（cosine < 0.3）
- **THEN** 系统返回空结果，AI 回复"未在知识库中找到相关内容，请尝试换个问法或上传相关文档"

#### Scenario: 权限过滤
- **WHEN** 系统检索文档时
- **THEN** 仅返回当前用户有 `read` 或 `manage` 权限的文档

#### Scenario: extracted_text 为空时回退
- **WHEN** 检索到的历史文档 `extracted_text` 为 NULL
- **THEN** 系统通过 `DocumentParser` 从文件存储重新解析文本，作为 Prompt 内容

### Requirement: 动态 Token 预算截断
构建 RAG Prompt 时，系统 SHALL 按总 token 预算动态分配每篇文档的截断长度。总预算为 32K token，按检索到的文档数均分，单篇上限 8000 字符。每篇文档在 Prompt 中以 `【序号】标题 (文件类型)` 为标题，附上截断后的文本内容。

#### Scenario: 5 篇文档均分预算
- **WHEN** 检索到 5 篇文档
- **THEN** 每篇文档截断至约 6400 字符（32K token / 5，按约 4 字符/token 估算）

#### Scenario: 1 篇文档使用上限
- **WHEN** 检索到 1 篇文档
- **THEN** 该文档截断至上限 8000 字符，不超过上限

### Requirement: 来源引用
每次 AI 回答 SHALL 附带引用的文档列表。引用信息包含文档名称、文件类型图标和相似度分数。前端展示为可点击的文档卡片，点击后打开文件预览或下载。

#### Scenario: 回答附带引用和分数
- **WHEN** AI 基于 2 份文档生成回答
- **THEN** 回答上方展示这 2 份文档的名称、类型图标和相似度分数（如"相关度 85%"）

#### Scenario: 点击引用查看原文
- **WHEN** 用户点击引用文档卡片
- **THEN** 系统展示该文档的原始文件（浏览器内联预览或打开下载）

### Requirement: 流式响应
AI 回答 SHALL 通过 SSE（Server-Sent Events）流式推送到前端，用户可看到逐字生成的回答。前端同时展示思考状态（"正在检索文档…" → "正在分析…"）。

#### Scenario: 流式推送过程
- **WHEN** 用户发送问题
- **THEN** 前端依次展示"检索中…" → 引用的文档卡片 → 逐字生成回答内容

#### Scenario: 连接中断处理
- **WHEN** SSE 连接中断
- **THEN** 前端展示"连接中断，正在重连…"并自动重试（最多 3 次）

### Requirement: 对话历史
系统 SHALL 保存知识库对话历史。用户可查看历史对话列表，点击恢复对话上下文。对话历史与项目 AI 对话完全独立存储。

#### Scenario: 创建新对话
- **WHEN** 用户首次进入知识库 AI 面板或点击"新建对话"
- **THEN** 系统创建新的 `knowledge_conversation`，标题默认为"新对话"

#### Scenario: 查看历史对话
- **WHEN** 用户点击历史对话列表中的某条
- **THEN** 系统加载该对话的完整消息历史，展示在问答面板中

#### Scenario: 对话自动标题
- **WHEN** 对话中产生第一轮问答后
- **THEN** 系统用用户首个问题的前 30 个字符作为对话标题
