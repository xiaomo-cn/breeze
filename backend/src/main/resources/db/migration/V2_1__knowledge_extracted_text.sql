-- V2_1: 知识库优化 —— 文档全文存储
-- 上传时将 Tika 提取的全文存入 extracted_text，问答时直接读取，避免重复解析

ALTER TABLE knowledge_documents
    ADD COLUMN IF NOT EXISTS extracted_text TEXT;

COMMENT ON COLUMN knowledge_documents.extracted_text IS 'Tika/DocumentParser 提取的纯文本全文。上传时填充，问答时直接读取。历史数据为 NULL 时回退到 DocumentParser 实时解析。';
