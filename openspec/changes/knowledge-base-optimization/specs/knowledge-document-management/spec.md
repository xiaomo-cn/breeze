## MODIFIED Requirements

### Requirement: 文档上传
系统 SHALL 支持用户上传多种格式的文档，包括 PDF、Word (.docx)、PPT (.pptx)、Excel (.xlsx)、Markdown (.md)、PNG、JPG、HTML、CSV、TXT。上传时 SHALL 计算文件 SHA-256 哈希值，若与已有文档哈希匹配则拒绝上传并提示"该文件已上传"。文档上传到当前浏览的文件夹中。上传成功后 SHALL 通过 `DocumentParser` 提取全文并存入 `extracted_text` 字段。

#### Scenario: 上传新文档到当前文件夹
- **WHEN** 用户在某个文件夹内选择文件并填写标题、标签、权限后点击"确认上传"
- **THEN** 文件原样保存到文件存储，全文通过 `DocumentParser` 提取并存入 `extracted_text`，`parent_folder_id` 设为当前文件夹 ID，触发异步向量化

#### Scenario: 重复文件被拒绝
- **WHEN** 用户上传的文件 SHA-256 哈希与已有文档匹配
- **THEN** 系统返回错误"该文件已上传"，不保存文件

#### Scenario: 文件大小超限
- **WHEN** 上传文件超过 50MB
- **THEN** 系统返回错误"文件大小不能超过 50MB"

### Requirement: 文件预览
系统 SHALL 根据文件类型返回正确的 MIME Content-Type，使浏览器能内联预览支持的格式（PDF、图片、文本、HTML）。不支持的格式以附件形式下载。

#### Scenario: PDF 内联预览
- **WHEN** 用户访问 PDF 文件的下载链接
- **THEN** 响应 Content-Type 为 `application/pdf`，浏览器内联渲染

#### Scenario: 图片内联预览
- **WHEN** 用户访问 PNG/JPG 文件的下载链接
- **THEN** 响应 Content-Type 为 `image/png` 或 `image/jpeg`，浏览器内联渲染

#### Scenario: 不支持格式下载
- **WHEN** 用户访问 docx/xlsx/pptx 文件的下载链接
- **THEN** 响应 Content-Type 为 `application/octet-stream`，浏览器触发下载

## ADDED Requirements

### Requirement: 删除确认
删除文档或文件夹时，前端 SHALL 弹出二次确认对话框，告知用户删除后果（文件夹可能包含子项，向量切片会被清理）。

#### Scenario: 删除文档确认
- **WHEN** 用户在网格或树形视图中点击"删除"
- **THEN** 弹出 Modal 确认框，显示"确定要删除「文档名」吗？删除后向量数据和文件将被清理"，用户点击"确认"后执行删除

#### Scenario: 删除非空文件夹确认
- **WHEN** 用户点击删除包含子项的文件夹
- **THEN** 弹出 Modal 确认框，显示"该文件夹包含 X 个子项，删除后全部内容将被清理"，用户点击"确认"后执行删除

### Requirement: 树形视图搜索
树形视图 SHALL 提供搜索框，支持按标题关键词在当前完整树中搜索文件和文件夹。

#### Scenario: 树形视图搜索
- **WHEN** 用户在树形视图的搜索框中输入关键词
- **THEN** 系统对文件标题做 ILIKE 模糊匹配，过滤树节点仅显示匹配项
